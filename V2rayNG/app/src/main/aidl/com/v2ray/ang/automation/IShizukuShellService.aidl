package com.v2ray.ang.automation;

interface IShizukuShellService {
    int runCommand(String command);
    void destroy() = 16777114;
}
