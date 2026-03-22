// src/api/dto/CocktailListResDto.ts
export interface CocktailListResDto {
  id: string;
  name: string;
  image?: string;
  category?: string;
  like?: number;
  dislike?: number;
}
