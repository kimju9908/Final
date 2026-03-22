import axiosInstance from "./AxiosInstance";
import Commons from "../util/Common";
import {AccessTokenDto, MyInfo} from "./dto/ReduxDto";
import axios from "axios";

const ReduxApi = {
  getMyInfo: async () => {
    return await axiosInstance.get<MyInfo>(Commons.BASE_URL + "/redux/myinfo");
  },
  refresh: async (refreshToken: string) => {
    return await axios.post<AccessTokenDto>(`${Commons.BASE_URL}/auth/tokens/refresh`, { refreshToken
    });
  },
};
export default ReduxApi;
