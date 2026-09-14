package com.byy.meterreading.auth.service;

import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.vo.auth.LoginVO;

public interface AuthService {

    LoginVO login(LoginDTO loginDTO);
}