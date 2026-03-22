import React, { useEffect, useState } from 'react';
import recipeDetailStyles from './style/RecipeDetailStyles';
import { useParams } from 'react-router-dom';
import { Card, CardContent, CardMedia, Typography, Grid, Box, CircularProgress, Alert, Divider } from '@mui/material';
import Comment from '../comment/Comment';

import RecipeApi from '../../api/RecipeApi';

 import { FoodResDto, ReactionSummaryDto } from '../../api/dto/RecipeDto';

import Profile from '../profile/Profile';
import LikeReportButtons from '../LikeReportButton'; 





const RecipeDetail: React.FC = () => {
    const [recipe, setRecipe] = useState<FoodResDto | null>(null);
    const [reactionSummary, setReactionSummary] = useState<ReactionSummaryDto | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string>('');
    const { id, type } = useParams<{ id: string; type: string }>();


    useEffect(() => {
        const getRecipe = async () => {
            try {
                const [detail, summary] = await Promise.all([
                    RecipeApi.fetchRecipeDetail(id!, type!),
                    RecipeApi.fetchReactionSummary(id!, type!),
                ]);
                setRecipe(detail);
                setReactionSummary(summary);
            } catch (err) {
                setError('레시피 상세 정보를 불러오는 데 실패했습니다.');
            } finally {
                setLoading(false);
            }
        };
    
        getRecipe();
    }, [id, type]);
    

    if (loading) return <CircularProgress />;
    if (error) return <Alert severity="error">{error}</Alert>;
    if (!recipe) return <Alert severity="warning">레시피를 찾을 수 없습니다.</Alert>;
    const resolvedReactionSummary = reactionSummary ?? {
        postId: id ?? "",
        type: type ?? "",
        likeCount: recipe.like,
        dislikeCount: recipe.dislike,
        currentUserReaction: "NONE" as const,
    };

    return (
        <Box sx={recipeDetailStyles.container}>
            <Card>
                <CardMedia component="img" image={recipe.image} alt={recipe.name} sx={recipeDetailStyles.cardMedia} />
                <CardContent sx={recipeDetailStyles.cardContent}>
                    <Typography variant="h3" component="h1" gutterBottom sx={recipeDetailStyles.title}>
                        {recipe.name}
                    </Typography>

                    <Grid container spacing={3} sx={recipeDetailStyles.gridContainer}>
                        <Grid item xs={12} md={6}>
                            <Typography variant="body1" color="text.secondary" sx={recipeDetailStyles.typography}>
                                <strong>조리 방법:</strong> {recipe.cookingMethod}
                            </Typography>
                        </Grid>
                        <Grid item xs={12} md={6}>
                            <Typography variant="body1" color="text.secondary" sx={recipeDetailStyles.typography}>
                                <strong>요리 종류:</strong> {recipe.category}
                            </Typography>
                        </Grid>
                    </Grid>

                    {/* 작성자 프로필 */}
                    <Box sx={recipeDetailStyles.profileBox}>
                        <Profile userId={recipe.author} customStyle={{ boxShadow: "none" }} />
                    </Box>

                    <Grid item xs={12} sx={recipeDetailStyles.descriptionBox}>
                        <Typography variant="body1" color="text.secondary" sx={recipeDetailStyles.typography}>
                            <strong>팁:</strong> {recipe.description}
                        </Typography>
                    </Grid>

                    {/* 재료 */}
                    <Typography variant="h5" component="h2" gutterBottom sx={recipeDetailStyles.sectionTitle}>
                        재료
                    </Typography>
                    <Divider sx={recipeDetailStyles.divider} />
                    <Grid container spacing={3} sx={recipeDetailStyles.ingredientGrid}>
                        {recipe.ingredients?.map((ingredient, index) => (
                            <Grid item xs={12} sm={6} md={4} key={index}>
                                <Typography variant="body1" sx={recipeDetailStyles.typography}>
                                    {`${ingredient.ingredient}: ${ingredient.amount}`}
                                </Typography>
                            </Grid>
                        ))}
                    </Grid>

                    {/* 조리 과정 */}
                    <Typography variant="h5" component="h2" gutterBottom sx={recipeDetailStyles.sectionTitle}>
                        조리 과정
                    </Typography>
                    <Divider sx={recipeDetailStyles.divider} />

                    {recipe.instructions?.map((manual, index) => (
                        <Grid container spacing={3} key={index} alignItems="flex-start" sx={recipeDetailStyles.instructionGrid}>
                            {/* 조리 과정 이미지 */}
                            {manual.imageUrl && (
                                <Grid item xs={12} md={6}>
                                    <CardMedia
                                        component="img"
                                        image={manual.imageUrl}
                                        alt={`조리 과정 ${index + 1}`}
                                        sx={recipeDetailStyles.instructionImage}
                                    />
                                </Grid>
                            )}

                            {/* 조리 과정 텍스트 */}
                            <Grid item xs={12} md={6}>
                                <Typography variant="h6" gutterBottom sx={recipeDetailStyles.stepTitle}>
                                    {`Step ${index + 1}`}
                                </Typography>
                                <Divider sx={recipeDetailStyles.divider} />
                                <Typography variant="body1" sx={recipeDetailStyles.typography}>
                                    {manual.text}
                                </Typography>
                            </Grid>
                        </Grid>
                    ))}

                    <Box sx={recipeDetailStyles.likeReportBox}>
                        <LikeReportButtons
                            postId={id ?? ""}
                            type={type ?? ""}
                            reactionSummary={resolvedReactionSummary}
                            onReactionChange={(summary) => {
                                setReactionSummary(summary);
                                setRecipe((prev) => (
                                    prev
                                        ? { ...prev, like: summary.likeCount, dislike: summary.dislikeCount }
                                        : prev
                                ));
                            }}
                        />
                    </Box>

                    {/* 댓글 섹션 */}
                    <Box sx={recipeDetailStyles.commentBox}>
                        <Comment postId={id ?? ""} />
                    </Box>
                </CardContent>
            </Card>
        </Box>
    );
};

export default RecipeDetail;
