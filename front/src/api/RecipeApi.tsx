import axios from "axios"
import axiosInstance from "./AxiosInstance";

import { FoodResDto,CocktailResDto,CocktailIngDto,CommentDto, RecommendedFoodDto, RecommendedCocktailDto, ReactionSummaryDto, ReactionType} from "./dto/RecipeDto";
const baseURL ='http://localhost:8111'
const RecipeApi = {

   
    saveRecipe: async (formData: FormData) => { // formData의 타입을 FormData로 지정
        try {
            const response = await axiosInstance.post("/recipe/save-recipe", formData, {
                headers: {
                    "Content-Type": "multipart/form-data", // 멀티파트 폼 데이터로 전송
                },
            });
            return response.data;
        } catch (error) {
            console.error("레시피 저장 실패:", error);
            throw error;
        }
    },
    saveCocktailRecipe: async (formData: FormData) => { // formData의 타입을 FormData로 지정
      try {
          const response = await axiosInstance.post("/recipe/save-cocktail-recipe", formData, {
              headers: {
                  "Content-Type": "multipart/form-data", // 멀티파트 폼 데이터로 전송
              },
          });
          return response.data;
      } catch (error) {
          console.error("레시피 저장 실패:", error);
          throw error;
      }
  },

  updateCocktailRecipe: async (formData: FormData) => { // formData의 타입을 FormData로 지정
    try {
        const response = await axiosInstance.post("/recipe/update-cocktail-recipe", formData, {
            headers: {
                "Content-Type": "multipart/form-data", // 멀티파트 폼 데이터로 전송
            },
        });
        return response.data;
    } catch (error) {
        console.error("레시피 저장 실패:", error);
        throw error;
    }
},
updateFoodRecipe: async (formData: FormData) => { // formData의 타입을 FormData로 지정
    try {
        const response = await axiosInstance.post("/recipe/update-recipe", formData, {
            headers: {
                "Content-Type": "multipart/form-data", // 멀티파트 폼 데이터로 전송
            },
        });
        return response.data;
    } catch (error) {
        console.error("레시피 저장 실패:", error);
        throw error;
    }
},



   fetchRecipeDetail : async (id: string, type: string): Promise<FoodResDto> => {
    const response = await axios.get<FoodResDto>(baseURL+`/test/detail/${id}?type=${type}`);
    return response.data;
},

fetchCocktail : async (id: string, type: string): Promise<CocktailResDto> => {
    const response = await axios.get<CocktailResDto>(baseURL+`/test/detail/${id}?type=${type}`);
    return response.data;
},


fetchComments: async (postId: string, page: number = 1, size: number = 5) => {
        const response = await axiosInstance.get<{ content: CommentDto[]; totalPages: number }>(
            `/recipes/${postId}/comments`,
            {
                params: { page: page - 1, size }, // 백엔드는 0부터 시작하는 페이지 인덱스
            }
        );
        return response.data;


},
    // 댓글 AI 요약 조회 (댓글 10개 이상일 때 생성, 20개마다 갱신)
    fetchCommentSummary: async (postId: string) => {
        const response = await axiosInstance.get<{
            summary: string | null;
            commentCount: number;
            updatedAt: string | null;
        }>(`/recipes/${postId}/comments/summary`);
        return response.data;
    },
addComment: async (postId: string, content: string) => {

        const response = await axiosInstance.post(`/recipes/${postId}/comments`, {
            content,
        });
        return response.data;
 
    },

    addReply: async (parentCommentId: number, content: string) => {
       
            const response = await axiosInstance.post(`/comments/${parentCommentId}/replies`, {
                content,
            });
            return response.data;
     
    },

    deleteComment: async (commentId: number) => {
 
            const response = await axiosInstance.delete(`/comments/${commentId}`);
            return response.data;  // 서버에서 반환된 응답 반환
      
    },

    updateReaction: async (postId: string, type: string, requestedReaction: ReactionType): Promise<ReactionSummaryDto> => {
        const response = await axiosInstance.post("/recipe/reaction", {
            postId,
            type,
            requestedReaction,
        });
        return response.data;
    },
    fetchReactionSummary: async (postId: string, type: string): Promise<ReactionSummaryDto> => {
        const response = await axios.get<ReactionSummaryDto>(baseURL + `/recipe/reaction-summary?postId=${postId}&type=${type}`);
        return response.data;
    },
    fetchRecommendedFoodsByLikes: async (): Promise<RecommendedFoodDto[]> => {
        const response = await axiosInstance.get("/recommend/food/likes");
        return response.data;
    },
    fetchRecommendedCocktailsByLikes: async (): Promise<RecommendedCocktailDto[]> => {
        const response = await axiosInstance.get("/recommend/cocktail/likes");
        return response.data;
    },

}

export default RecipeApi;
