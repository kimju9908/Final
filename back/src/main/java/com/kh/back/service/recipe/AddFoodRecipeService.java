package com.kh.back.service.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.back.dto.recipe.request.AddFoodRecipeDto;
import com.kh.back.service.FirebaseService;
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
    private FirebaseService firebaseService;
    @Autowired
    private ObjectMapper objectMapper;

    // ✅ 이미지 파일 여부 확인


    // ✅ 공통 레시피 데이터 생성 메서드
    private Map<String, Object> createRecipeData(Long memberId, AddFoodRecipeDto recipeRequest) throws IOException {
        // 대표 이미지 업로드 (파일이 있을 경우에만 업로드)
        String mainImageUrl;
        if (recipeRequest.getAttFileNoMain() != null) {
            // 새로 추가한 메인 이미지 파일이 있는 경우
            mainImageUrl = firebaseService.uploadImage(recipeRequest.getAttFileNoMain(), recipeRequest.getName());
        } else if (recipeRequest.getExistingMainImageUrl() != null) {
            // 기존 메인 이미지 URL이 있는 경우
            mainImageUrl = recipeRequest.getExistingMainImageUrl();
        } else {
            // 이미지가 없는 경우
            mainImageUrl = null;
        }

        // 매뉴얼 이미지 업로드 (파일이 있을 경우에만 업로드)
        List<Map<String, String>> manualsWithUrls = recipeRequest.getManuals().stream()
                .map(manual -> {
                    try {
                        String imageUrl;
                        if (manual.getNewImageFile() != null) {
                            // 새로 추가한 조리법 이미지 파일이 있는 경우
                            imageUrl = firebaseService.uploadImage(manual.getNewImageFile(), recipeRequest.getName());
                        } else if (manual.getExistingImageUrl() != null) {
                            // 기존 조리법 이미지 URL이 있는 경우
                            imageUrl = manual.getExistingImageUrl();
                        } else {
                            // 이미지가 없는 경우
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

        // 레시피 데이터 생성
        Map<String, Object> recipeData = new HashMap<>();
        recipeData.put("updateId", recipeRequest.getPostId());
        recipeData.put("type", recipeRequest.getType());
        recipeData.put("name", recipeRequest.getName());
        recipeData.put("RCP_WAY2", recipeRequest.getRcpWay2());
        recipeData.put("RCP_PAT2", recipeRequest.getRcpPat2());
        recipeData.put("INFO_WGT", recipeRequest.getInfoWgt());
        recipeData.put("ATT_FILE_NO_MAIN", mainImageUrl);
        recipeData.put("RCP_NA_TIP", recipeRequest.getRcpNaTip());
        recipeData.put("ingredients", recipeRequest.getIngredients());
        recipeData.put("MANUALS", manualsWithUrls);
        recipeData.put("like", 0L);
        recipeData.put("dislike", 0L);
        recipeData.put("author", memberId);

        return recipeData;
    }

    // ✅ 새로운 레시피 저장
    public String saveRecipe(Long memberId, AddFoodRecipeDto recipeRequest) {
        try {
            Map<String, Object> recipeData = createRecipeData(memberId, recipeRequest);
            String data = objectMapper.writeValueAsString(recipeData);
            return elasticService.uploadRecipe(data);
        } catch (IOException e) {
            return "레시피 저장 중 오류 발생: " + e.getMessage();
        }
    }

    // ✅ 기존 레시피 업데이트
    public String updateRecipe(Long memberId, AddFoodRecipeDto recipeRequest) {
        try {
            Map<String, Object> recipeData = createRecipeData(memberId, recipeRequest);
            String data = objectMapper.writeValueAsString(recipeData);
            return elasticService.updateRecipe(data);
        } catch (IOException e) {
            return "레시피 업데이트 중 오류 발생: " + e.getMessage();
        }
    }
}
