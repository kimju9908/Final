import axiosInstance from "./AxiosInstance";
import Commons from "../util/Common";
import {MyInfo} from "./dto/ReduxDto";
import axios from "axios";
import {loginResDto} from "./dto/AuthDto";

const ReduxApi = {
  getMyInfo: async () => {
    return await axiosInstance.get<MyInfo>(Commons.BASE_URL + "/redux/myinfo");
  },
  refresh: async (refreshToken: string) => {
    return await axios.post<loginResDto>(`${Commons.BASE_URL}/auth/tokens/refresh`, { refreshToken
    });
  },
};
export default ReduxApi;
