export interface MyInfo {
  id: number;
  email: string;
  nickname: string;
  role: "ROLE_ADMIN" | "ROLE_USER";
  likedRecipes: Set<string>; // 좋아요한 레시피 ID 목록
  dislikedRecipes: Set<string>; // 싫어요한 레시피 ID 목록
  premium: boolean; // 프리미엄 회원 여부 추가
}

export interface AccessTokenDto {
  grantType: string;
  accessToken: string;
  accessTokenExpires: number;
}
