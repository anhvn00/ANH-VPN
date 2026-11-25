package com.v2ray.ang.automation;

interface IShizukuShellService {
    void destroy() = 16777114;
    int runCommand(String command) = 1;
}
