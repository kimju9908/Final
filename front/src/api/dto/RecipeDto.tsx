export interface IngredientDto {
    ingredient: string;
    amount: string;
}

export interface ManualDto {
    text: string;
    imageUrl: string;
}

export interface FoodResDto {
    name: string;
    cookingMethod: string;
    category: string;
    description: string;
    ingredients: IngredientDto[];
    instructions: ManualDto[];
    image: string;
    like: number;
    dislike: number;
    author: number;
}

export interface CocktailIngDto {
    unit: string;
    amount: number;
    ingredient: string;
    special?: string;
}

// 칵테일 상세 정보 DTO 타입 정의
export interface CocktailResDto {
    id: string;
    name: string;
    preparation: string;
    image: string;
    category: string;
    abv: number;
    garnish: string;
    glass: string;
    like: number;
    dislike: number;
    author: number;
    ingredients: CocktailIngDto[];
}

export interface CommentDto {
    memberId: number;
    commentId: number;
    nickName: string;
    content: string;
    parentCommentId: number | null;
    deleted: boolean;
    replies: CommentDto[];
}

export interface CommentSectionProps {
    postId: string; // 댓글이 속한 게시물 ID
}

export type ReactionType = "LIKE" | "DISLIKE" | "NONE";

export interface ReactionSummaryDto {
    postId: string;
    type: string;
    likeCount: number;
    dislikeCount: number;
    currentUserReaction: ReactionType;
}

export interface LikeReportButtonsProps {
    postId: string;
    type: string;
    reactionSummary: ReactionSummaryDto;
    onReactionChange: (summary: ReactionSummaryDto) => void;
}

export interface RecommendedFoodDto {
    id: string;
    name: string;
    image?: string;
    category?: string;
    like: number;
    author: number;
    reason: string;
    recommendationScore: number;
}

export interface RecommendedCocktailDto {
    id: string;
    name: string;
    image?: string;
    category?: string;
    like: number;
    author: number;
    reason: string;
    recommendationScore: number;
}
