import { AppDispatch, RootState } from "../../context/Store";
import { useDispatch, useSelector } from "react-redux";
import { IconButton, Tooltip } from "@mui/material";
import PersonIcon from "@mui/icons-material/Person";
import React, { useState } from "react";
import { openModal } from "../../context/redux/ModalReducer";
import { useNavigate } from "react-router-dom";
import { handleLogout } from "../../context/redux/CommonAction";
import CloseIcon from '@mui/icons-material/Close';
import DropdownComponent from "../../component/DropdownComponent";
import { profileButtonStyles } from "./style/ProfileButtonStyles";

const ProfileButton = () => {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const guest = useSelector((state: RootState) => state.user.guest);
  const admin = useSelector((state: RootState) => state.user.admin);
  const nickname = useSelector((state: RootState) => state.user.nickname);
  const [profile, setProfile] = useState<boolean>(false);

  const menuList = guest
    ? [
      { name: "로그인", fn: () => dispatch(openModal("login")) },
      { name: "회원 가입", fn: () => dispatch(openModal("signup")) },
    ]
    : admin
      ? [
        { name: "프로필", fn: () => navigate("/profile") },
        { name: "관리자 페이지", fn: () => navigate("/admin") },
        { name: "로그아웃", fn: () => dispatch(handleLogout()) },
      ]
      : [
        { name: "프로필", fn: () => navigate("/profile") },
        { name: "로그아웃", fn: () => dispatch(handleLogout()) },
      ];

  return (
    <>
      <Tooltip title={profile ? "닫기" : (nickname || "게스트")} >
        <IconButton
          onClick={() => setProfile(!profile)}
          sx={profileButtonStyles.iconButton(profile)}>
          {profile ? <CloseIcon/> : <PersonIcon/>}
        </IconButton>
      </Tooltip>
      <DropdownComponent open={profile} onClose={() => setProfile(false)} list={menuList} />
    </>
  );
};
export default ProfileButton;