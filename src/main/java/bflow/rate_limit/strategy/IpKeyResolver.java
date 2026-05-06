package bflow.rate_limit.strategy;

import bflow.rate_limit.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IpKeyResolver implements KeyResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        return "ip:" + ClientIpUtil.extract(request);
    }
}