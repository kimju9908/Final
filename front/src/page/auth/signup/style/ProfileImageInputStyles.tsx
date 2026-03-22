import styled from "styled-components";

export const ProfileWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
`;

export const ProfileImage = styled.div`
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  justify-content: center;
  align-items: center;
`;

export const FileInputLabel = styled.label`
  position: absolute;
  bottom: 5px;
  right: 5px;
  background-color: rgba(0, 0, 0, 0.5);
  color: white;
  width: 30px;
  height: 30px;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 50%;
  cursor: pointer;
  z-index: 10;
  box-sizing: border-box;
`;

export const FileInput = styled.input`
  display: none;
`;

export const profileImageStyles :{image: React.CSSProperties} = {
  image: {
    width: "100%",
    height: "100%",
    objectFit: "cover",
  },
};

