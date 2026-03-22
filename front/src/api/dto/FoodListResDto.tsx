// src/dto/FoodListResDto.ts
export interface FoodListResDto {
  id: string;
  name: string;
  image?: string;
  category?: string;
  like?: number;
  dislike?: number;
}
