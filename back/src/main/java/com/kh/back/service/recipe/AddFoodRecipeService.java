package com.kh.back.service.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.back.dto.recipe.request.AddFoodRecipeDto;
import com.kh.back.dto.recipe.res.DirectUploadTestResDto;
import com.kh.back.service.FirebaseService;
import com.kh.back.service.python.DirectElasticIndexService;
import com.kh.back.service.python.ElasticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AddFoodRecipeService {
    @Autowired
    private ElasticService elasticService;
    @Autowired
    private DirectElasticIndexService directElasticIndexService;
    @Autowired
    private FirebaseService firebaseService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecipePostService recipePostService;

    /**
     * ES에 저장할 음식 레시피 데이터 생성 (like, dislike, author 제외)
     */
    private Map<String, Object> createEsData(AddFoodRecipeDto recipeRequest) throws IOException {
        // 대표 이미지 업로드
        String mainImageUrl;
        if (recipeRequest.getAttFileNoMain() != null) {
            mainImageUrl = firebaseService.uploadImage(recipeRequest.getAttFileNoMain(), recipeRequest.getName());
        } else if (recipeRequest.getExistingMainImageUrl() != null) {
            mainImageUrl = recipeRequest.getExistingMainImageUrl();
        } else {
            mainImageUrl = null;
        }

        // 매뉴얼 이미지 업로드
        List<Map<String, String>> manualsWithUrls = recipeRequest.getManuals().stream()
                .map(manual -> {
                    try {
                        String imageUrl;
                        if (manual.getNewImageFile() != null) {
                            imageUrl = firebaseService.uploadImage(manual.getNewImageFile(), recipeRequest.getName());
                        } else if (manual.getExistingImageUrl() != null) {
                            imageUrl = manual.getExistingImageUrl();
                        } else {
                            imageUrl = null;
                        }
                        Map<String, String> manualMap = new HashMap<>();
                        manualMap.put("text", manual.getText());
                        manualMap.put("imageUrl", imageUrl);
                        return manualMap;
                    } catch (IOException e) {
                        throw new RuntimeException("이미지 업로드 실패: " + e.getMessage());
                    }
                })
                .collect(Collectors.toList());

        // ES 저장용 데이터 (like, dislike, author 제외)
        Map<String, Object> esData = new HashMap<>();
        esData.put("updateId", recipeRequest.getPostId());
        esData.put("type", recipeRequest.getType());
        esData.put("name", recipeRequest.getName());
        esData.put("RCP_WAY2", recipeRequest.getRcpWay2());
        esData.put("RCP_PAT2", recipeRequest.getRcpPat2());
        esData.put("INFO_WGT", recipeRequest.getInfoWgt());
        esData.put("ATT_FILE_NO_MAIN", mainImageUrl);
        esData.put("RCP_NA_TIP", recipeRequest.getRcpNaTip());
        esData.put("ingredients", recipeRequest.getIngredients());
        esData.put("MANUALS", manualsWithUrls);
        // like, dislike, author 는 DB(Reaction, RecipePost)에서 관리 → ES 저장 안 함

        return esData;
    }

    /** ES 응답 JSON에서 _id 추출 */
    private String extractEsDocId(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            return node.path("id").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ✅ 새로운 레시피 저장
    public String saveRecipe(Long memberId, AddFoodRecipeDto recipeRequest) {
        try {
            Map<String, Object> esData = createEsData(recipeRequest);
            String jsonData = objectMapper.writeValueAsString(esData);
            String response = elasticService.uploadRecipe(jsonData);

            // DB에 소유권 저장
            String esDocId = extractEsDocId(response);
            if (esDocId != null) {
                recipePostService.saveRecipePost(
                        memberId, esDocId, "food", recipeRequest.getName());
            }
            return response;
        } catch (IOException e) {
            return "레시피 저장 중 오류 발생: " + e.getMessage();
        }
    }

    // ✅ 기존 레시피 업데이트
    public String updateRecipe(Long memberId, AddFoodRecipeDto recipeRequest) {
        try {
            Map<String, Object> esData = createEsData(recipeRequest);
            String jsonData = objectMapper.writeValueAsString(esData);
            return elasticService.updateRecipe(jsonData);
        } catch (IOException e) {
            return "레시피 업데이트 중 오류 발생: " + e.getMessage();
        }
    }

    public DirectUploadTestResDto saveRecipeDirect(Long memberId, AddFoodRecipeDto recipeRequest) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> esData = createEsData(recipeRequest);
            String result = directElasticIndexService.uploadRecipe(esData);

            // Direct 업로드 시에도 DB 저장
            String esDocId = extractEsDocId(result);
            if (esDocId != null) {
                recipePostService.saveRecipePost(
                        memberId, esDocId, "food", recipeRequest.getName());
            }
            return DirectUploadTestResDto.builder()
                    .mode("spring-direct-es")
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .result(result)
                    .build();
        } catch (Exception e) {
            return DirectUploadTestResDto.builder()
                    .mode("spring-direct-es")
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .result("레시피 저장 중 오류 발생: " + e.getMessage())
                    .build();
        }
    }

    public DirectUploadTestResDto updateRecipeDirect(Long memberId, AddFoodRecipeDto recipeRequest) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> esData = createEsData(recipeRequest);
            String result = directElasticIndexService.updateRecipe(esData);
            return DirectUploadTestResDto.builder()
                    .mode("spring-direct-es")
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .result(result)
                    .build();
        } catch (Exception e) {
            return DirectUploadTestResDto.builder()
                    .mode("spring-direct-es")
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .result("레시피 업데이트 중 오류 발생: " + e.getMessage())
                    .build();
        }
    }
}
