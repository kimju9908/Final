import {EmailTokenVerificationDto, loginReqDto, loginResDto, SmsTokenVerificationDto,} from "./dto/AuthDto";
import axios from "axios";
import Commons from "../util/Common";

const AuthApi = {
  login: async (loginReq: loginReqDto) => {
    return await axios.post<loginResDto>(
      Commons.BASE_URL + "/auth/tokens",
      loginReq
    );
  },
  emailExists: async (email: string) => {
    return await axios.get<boolean>(Commons.BASE_URL + `/auth/emails/exists`, {
      params: { email },
    });
  },
  nicknameExists: async (nickname: string) => {
    return await axios.get<boolean>(Commons.BASE_URL + `/auth/nicknames/exists`, {
      params: { nickname },
    });
  },
  phoneExists: async (phone: string) => {
    return await axios.get<boolean>(Commons.BASE_URL + `/auth/phones/exists`, {
      params: { phone },
    });
  },

  signup: async (formData: FormData) => {
    try {
      return await axios.post(Commons.BASE_URL + `/auth/registrations`, formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });
    } catch (error) {
      console.error("Signup error:", error);
      throw error;
    }
  },
  verifySmsToken: async (verify: SmsTokenVerificationDto) => {
    return await axios.post<boolean>(
      Commons.BASE_URL + `/auth/sms-verifications`,
      verify
    );
  },
  sendVerificationCode: async (phone: string) => {
    console.log(phone);
    return await axios.post<string>(Commons.BASE_URL + `/auth/sms-verification-codes`, { phone });
  },
  changePassword: async (pwd: string) => {
    return await axios.patch<boolean>(
      Commons.BASE_URL + "/auth/password",
      { password: pwd }
    );
  },
  sendPw: async (email: string) => {
    return await axios.post<boolean>(Commons.BASE_URL + "/auth/password-reset-tokens", {email});
  },
  verifyEmailToken: async (request: EmailTokenVerificationDto) => {
    return await axios.post<boolean>(
      Commons.BASE_URL + "/auth/email-verifications",
      request
    );
  },
  findEmailByPhone: async (phone: string) => {
    return await axios.get<string>(Commons.BASE_URL + `/auth/accounts/email`, {
      params: { phone },
    });
  },
};
export default AuthApi;
