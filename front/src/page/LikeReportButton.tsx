import React from 'react';
import { Box, IconButton, Typography } from '@mui/material';
import FavoriteIcon from '@mui/icons-material/Favorite';
import ThumbDownIcon from '@mui/icons-material/ThumbDown';
import { useDispatch, useSelector } from 'react-redux';
import { RootState } from '../context/Store';
import { setRecipeReaction } from '../context/redux/UserReducer';
import RecipeApi from '../api/RecipeApi';
import { AxiosError } from 'axios';
import { LikeReportButtonsProps, ReactionType } from '../api/dto/RecipeDto';
import { likeReportButtonStyles } from './LikeReportButtonStyles';

const normalizeRecipeSet = (value: unknown): Set<string> => {
    if (value instanceof Set) {
        return value;
    }
    if (Array.isArray(value)) {
        return new Set(value.map(String));
    }
    return new Set();
};

const LikeReportButtons: React.FC<LikeReportButtonsProps> = ({ postId, type, reactionSummary, onReactionChange }) => {
    const dispatch = useDispatch();
    const { likedRecipes, dislikedRecipes } = useSelector((state: RootState) => state.user);
    const likedRecipeSet = normalizeRecipeSet(likedRecipes);
    const dislikedRecipeSet = normalizeRecipeSet(dislikedRecipes);
    const recipeKey = `${type}:${postId}`;
    const isLiked = reactionSummary.currentUserReaction === "LIKE" || likedRecipeSet.has(recipeKey);
    const isDisliked = reactionSummary.currentUserReaction === "DISLIKE" || dislikedRecipeSet.has(recipeKey);

    const handleReaction = async (requestedReaction: ReactionType) => {
        try {
            const summary = await RecipeApi.updateReaction(postId, type, requestedReaction);
            onReactionChange(summary);
            dispatch(setRecipeReaction({ recipeKey, reaction: summary.currentUserReaction }));
        } catch (error) {
            if (error instanceof AxiosError && error.response?.status === 401) {
                alert('반응 기능은 로그인 후 사용 가능합니다.');
            } else {
                console.error("반응 처리 중 오류 발생:", error);
            }
        }
    };

    const toggleLike = async () => {
        await handleReaction("LIKE");
    };

    const toggleDislike = async () => {
        await handleReaction("DISLIKE");
    };

    return (
        <Box sx={likeReportButtonStyles.container}>
            <Box sx={likeReportButtonStyles.countBox}>
                <Typography fontSize={20} color="text.secondary" sx={likeReportButtonStyles.countText}>
                    <strong>좋아요:</strong> {reactionSummary.likeCount}
                </Typography>
                <Typography fontSize={20} color="text.secondary" sx={likeReportButtonStyles.countText}>
                    <strong>싫어요:</strong> {reactionSummary.dislikeCount}
                </Typography>
            </Box>

            <Box sx={likeReportButtonStyles.buttonBox}>
                <IconButton onClick={toggleLike} sx={likeReportButtonStyles.likeButton(isLiked)}>
                    <FavoriteIcon sx={likeReportButtonStyles.iconStyle} />
                </IconButton>
                <IconButton onClick={toggleDislike} sx={likeReportButtonStyles.reportButton(isDisliked)}>
                    <ThumbDownIcon sx={likeReportButtonStyles.iconStyle} />
                </IconButton>
            </Box>
        </Box>
    );
};

export default LikeReportButtons;
