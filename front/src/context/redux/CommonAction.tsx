import { createAction } from "@reduxjs/toolkit";
import { AppDispatch, persistor, store } from "../Store";
import AuthApi from "../../api/AuthApi";

export const logout = createAction('common/logout');

export const handleLogout = () => {
  return async (dispatch: AppDispatch) => {
    // 1. 서버에 로그아웃 요청 (Refresh Token 삭제 + Access Token 블랙리스트 등록)
    try {
      const accessToken = store.getState().token.accessToken;
      if (accessToken) {
        await AuthApi.logout(accessToken);
      }
    } catch (e) {
      // 서버 요청 실패해도 클라이언트 로그아웃은 진행
      console.warn("[logout] 서버 로그아웃 요청 실패:", e);
    }

    // 2. Redux 상태 초기화
    dispatch(logout());

    // 3. 로컬스토리지(persist) 초기화
    await persistor.purge();
  };
};
