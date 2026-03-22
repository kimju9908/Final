import styled from "styled-components";

export const InputField = styled.input`
    width: 100%;
    padding: 12px;
    margin: 10px 0;
    border-radius: 20px;
    box-sizing: border-box;
    font-size: 16px;
    border: 1px solid #d1b6a3;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    &:focus {
        border-color: #d1b6a3;
        outline: none;
    }
`;

export const TimerText = styled.p`
    color: red;
    font-size: 14px;
    margin-top: 5px;
`;

export const Button = styled.button`
    width: 100%;
    padding: 12px;
    background-color: #6a4e23;
    color: white;
    border: none;
    border-radius: 20px;
    cursor: pointer;
    font-size: 16px;
    margin-top: 10px;

    &:hover {
        background-color: #6a4e23;
    }

    &:disabled {
        background-color: #d1b6a3;
        cursor: not-allowed;
    }
`;

