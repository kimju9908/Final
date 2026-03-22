package com.kh.back.service.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kh.back.dto.recipe.request.AddCocktailRecipeDto;
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
public class AddCocktailRecipeService {
    @Autowired
    private ElasticService elasticService;
    @Autowired
    private FirebaseService firebaseService;
    @Autowired
    private ObjectMapper objectMapper;


    private boolean isFile(String image) {
        // 파일인지 URL인지 구별하는 로직 (단순히 URL 형식이 아닌 경우 파일로 처리)
        return !image.startsWith("http") && !image.startsWith("https");
    }

    private Map<String, Object> createRecipeData(Long memberId, AddCocktailRecipeDto recipeRequest) throws IOException {
        String image;


        if (recipeRequest.getImage() != null) {
            // 새로 추가한 메인 이미지 파일이 있는 경우
            image = firebaseService.uploadImage(recipeRequest.getImage(), recipeRequest.getName());
        } else if (recipeRequest.getExistingImage() != null) {
            // 기존 메인 이미지 URL이 있는 경우
            image = recipeRequest.getExistingImage();
        } else {
            // 이미지가 없는 경우
            image = null;
        }

        Map<String, Object> recipeData = new HashMap<>();
        recipeData.put("updateId", recipeRequest.getPostId());
        recipeData.put("type", recipeRequest.getType());
        recipeData.put("name", recipeRequest.getName());
        recipeData.put("glass", recipeRequest.getGlass());
        recipeData.put("category", recipeRequest.getCategory());
        recipeData.put("ingredients", recipeRequest.getIngredients());
        recipeData.put("garnish", recipeRequest.getGarnish());
        recipeData.put("preparation", recipeRequest.getPreparation());
        recipeData.put("abv", recipeRequest.getAbv());
        recipeData.put("like", 0L);
        recipeData.put("dislike", 0L);
        recipeData.put("author", memberId);
        recipeData.put("image", image);

        return recipeData;
    }

    public String saveCocktailRecipe(Long memberId, AddCocktailRecipeDto recipeRequest) {
        try {
            Map<String, Object> recipeData = createRecipeData(memberId, recipeRequest);
            String data = objectMapper.writeValueAsString(recipeData);
            return elasticService.uploadRecipe(data);
        } catch (IOException e) {
            return "레시피 저장 중 오류 발생: " + e.getMessage();
        }
    }

    public String updateCocktailRecipe(Long memberId, AddCocktailRecipeDto recipeRequest) {
        try {
            Map<String, Object> recipeData = createRecipeData(memberId, recipeRequest);
            String data = objectMapper.writeValueAsString(recipeData);
            return elasticService.updateRecipe(data);
        } catch (IOException e) {
            return "레시피 업데이트 중 오류 발생: " + e.getMessage();
        }
    }



}
