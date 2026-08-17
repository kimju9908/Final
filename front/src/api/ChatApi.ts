import axiosInstance from "./AxiosInstance";

export interface ChatRecipe {
  id: string;
  name: string;
  image: string;
  category: string;
}

export interface ChatRecipeResDto {
  ingredients: string[];
  recipes: ChatRecipe[];
  message: string;
}

const ChatApi = {
  chatRecipe: async (message: string, type: "food" | "cocktail" = "food"): Promise<ChatRecipeResDto> => {
    const response = await axiosInstance.post("/api/chat/recipe", { message, type });
    return response.data;
  },
};

export default ChatApi;
