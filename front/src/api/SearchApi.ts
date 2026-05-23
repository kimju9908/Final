import axiosInstance from "./AxiosInstance";

export interface SearchSuggestionResDto {
  type: "POPULAR" | "AUTOCOMPLETE";
  items: string[];
}

const SearchApi = {
  getSuggestions: async (
    searchType: "FOOD" | "COCKTAIL",
    keyword: string = "",
    signal?: AbortSignal
  ): Promise<SearchSuggestionResDto> => {
    const response = await axiosInstance.get("/api/search/suggestions", {
      params: { searchType, keyword },
      signal,
    });
    return response.data;
  },
};

export default SearchApi;
