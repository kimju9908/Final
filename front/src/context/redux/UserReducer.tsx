import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { UserState } from "../types";
import { logout } from "./CommonAction";
import { MyInfo } from "../../api/dto/ReduxDto";

const normalizeRecipeSet = (value: unknown): Set<string> => {
  if (value instanceof Set) {
    return new Set(value);
  }
  if (Array.isArray(value)) {
    return new Set(value.map(String));
  }
  return new Set();
};

const initialState: UserState = {
  id: null,
  email: "",
  nickname: "",
  guest: true,
  admin: false,
  premium: false, // 추가된 프리미엄 상태
  likedRecipes: new Set(),
  dislikedRecipes: new Set(),
};

const UserReducer = createSlice({
  name: "user",
  initialState,
  reducers: {
    setUserInfo: (state, action: PayloadAction<MyInfo>) => {
      state.id = action.payload.id;
      state.email = action.payload.email;
      state.nickname = action.payload.nickname;
      state.guest = false;
      state.admin = action.payload.role === "ROLE_ADMIN";
      state.premium = action.payload.premium; // 프리미엄 상태를 저장합니다.

      // 기존 유저의 좋아요 및 신고 리스트 업데이트 (있다면)
      if (action.payload.likedRecipes) {
        state.likedRecipes = normalizeRecipeSet(action.payload.likedRecipes);
      }
      if (action.payload.dislikedRecipes) {
        state.dislikedRecipes = normalizeRecipeSet(action.payload.dislikedRecipes);
      }
    },
    setGuest: (state) => {
      state.guest = true;
      state.id = null;
      state.email = "";
      state.nickname = "";
      state.admin = false;
      state.likedRecipes = new Set();
      state.dislikedRecipes = new Set();
    },
    setRecipeReaction: (
      state,
      action: PayloadAction<{ recipeKey: string; reaction: "LIKE" | "DISLIKE" | "NONE" }>
    ) => {
      const { recipeKey, reaction } = action.payload;
      state.likedRecipes = normalizeRecipeSet(state.likedRecipes);
      state.dislikedRecipes = normalizeRecipeSet(state.dislikedRecipes);

      state.likedRecipes.delete(recipeKey);
      state.dislikedRecipes.delete(recipeKey);

      if (reaction === "LIKE") {
        state.likedRecipes.add(recipeKey);
      }
      if (reaction === "DISLIKE") {
        state.dislikedRecipes.add(recipeKey);
      }
    },
  },
  extraReducers: (builder) => {
    builder.addCase(logout, (state) => {
      state.id = null;
      state.email = "";
      state.guest = true;
      state.nickname = "";
      state.admin = false;
      state.premium = false;
      state.likedRecipes = new Set();
      state.dislikedRecipes = new Set();
    });
  },
});

export const { setUserInfo, setGuest, setRecipeReaction } =
  UserReducer.actions;
export default UserReducer.reducer;
