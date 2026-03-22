import React, { useState } from "react";
import { CgProfile } from "react-icons/cg";
import {
  ProfileWrapper,
  ProfileImage,
  FileInputLabel,
  FileInput,
  profileImageStyles,
} from "./style/ProfileImageInputStyles";

interface ProfileImageInputProps {
  onFileChange: (file: File | null) => void;
}

const ProfileImageInput: React.FC<ProfileImageInputProps> = ({ onFileChange }) => {
  const [selectedImage, setSelectedImage] = useState<string | null>(null);

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files ? e.target.files[0] : null;
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setSelectedImage(reader.result as string); // 파일을 읽어서 미리보기
      };
      reader.readAsDataURL(file);
      onFileChange(file); // 부모 컴포넌트로 파일 전달
    } else {
      onFileChange(null); // 파일 선택이 취소된 경우 null 전달
    }
  };

  return (
    <ProfileWrapper>
      <ProfileImage>
        {selectedImage ? (
          <img src={selectedImage} alt="Profile" style={profileImageStyles.image} />
        ) : (
          <CgProfile size={140} color="#6a4e23" />
        )}
        <FileInputLabel htmlFor="profileImage">
          +
          <FileInput
            type="file"
            id="profileImage"
            accept="image/*"
            onChange={handleFileInputChange}
          />
        </FileInputLabel>
      </ProfileImage>
    </ProfileWrapper>
  );
};

export default ProfileImageInput;
  