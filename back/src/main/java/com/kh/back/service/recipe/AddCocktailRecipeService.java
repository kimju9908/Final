package com.kh.back.service.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.back.dto.recipe.request.AddCocktailRecipeDto;
import com.kh.back.dto.recipe.res.DirectUploadTestResDto;
import com.kh.back.service.FirebaseService;
import com.kh.back.service.python.DirectElasticIndexService;
import com.kh.back.service.python.ElasticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class AddCocktailRecipeService {
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
     * ES에 저장할 칵테일 레시피 데이터 생성 (like, dislike, author 제외)
     */
    private Map<String, Object> createEsData(AddCocktailRecipeDto recipeRequest) throws IOException {
        String image;
        if (recipeRequest.getImage() != null) {
            image = firebaseService.uploadImage(recipeRequest.getImage(), recipeRequest.getName());
        } else if (recipeRequest.getExistingImage() != null) {
            image = recipeRequest.getExistingImage();
        } else {
            image = null;
        }

        Map<String, Object> esData = new HashMap<>();
        esData.put("updateId", recipeRequest.getPostId());
        esData.put("type", recipeRequest.getType());
        esData.put("name", recipeRequest.getName());
        esData.put("glass", recipeRequest.getGlass());
        esData.put("category", recipeRequest.getCategory());
        esData.put("ingredients", recipeRequest.getIngredients());
        esData.put("garnish", recipeRequest.getGarnish());
        esData.put("preparation", recipeRequest.getPreparation());
        esData.put("abv", recipeRequest.getAbv());
        esData.put("image", image);
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

    public String saveCocktailRecipe(Long memberId, AddCocktailRecipeDto recipeRequest) {
        try {
            Map<String, Object> esData = createEsData(recipeRequest);
            String jsonData = objectMapper.writeValueAsString(esData);
            String response = elasticService.uploadRecipe(jsonData);

            // DB에 소유권 저장
            String esDocId = extractEsDocId(response);
            if (esDocId != null) {
                recipePostService.saveRecipePost(
                        memberId, esDocId, "cocktail", recipeRequest.getName());
            }
            return response;
        } catch (IOException e) {
            return "레시피 저장 중 오류 발생: " + e.getMessage();
        }
    }

    public String updateCocktailRecipe(Long memberId, AddCocktailRecipeDto recipeRequest) {
        try {
            Map<String, Object> esData = createEsData(recipeRequest);
            String jsonData = objectMapper.writeValueAsString(esData);
            return elasticService.updateRecipe(jsonData);
        } catch (IOException e) {
            return "레시피 업데이트 중 오류 발생: " + e.getMessage();
        }
    }

    public DirectUploadTestResDto saveCocktailRecipeDirect(Long memberId, AddCocktailRecipeDto recipeRequest) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> esData = createEsData(recipeRequest);
            String result = directElasticIndexService.uploadRecipe(esData);

            // Direct 업로드 시에도 DB 저장 (응답에서 id 파싱)
            String esDocId = extractEsDocId(result);
            if (esDocId != null) {
                recipePostService.saveRecipePost(
                        memberId, esDocId, "cocktail", recipeRequest.getName());
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
                    .result("칵테일 저장 중 오류 발생: " + e.getMessage())
                    .build();
        }
    }

    public DirectUploadTestResDto updateCocktailRecipeDirect(Long memberId, AddCocktailRecipeDto recipeRequest) {
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
                    .result("칵테일 업데이트 중 오류 발생: " + e.getMessage())
                    .build();
        }
    }
}
