// Copyright (c) 2026 XtraCube
#include <unistd.h>
#include <jni.h>
#include <cctype>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <mutex>
#include <random>
#include <sstream>
#include <unordered_map>
#include <logger.h>
#include <libmain.h>
#include <fusion_config.h>
#include <hooking/il2cpp.h>
#include <hooking/safehook.h>
#include <hooking/allocator.h>
#include <hooking/libunity.h>
#include <dotnet.h>
#include <external/dobby.h>
#include <external/xdl.h>

#define TAG "FusionCore"

namespace fs = std::filesystem;

static FusionConfig runtimeConfig;
static FusionConfig stagedConfig;
static std::string stagedPatchedIl2CppPath;
static std::mutex stageMutex;
static bool hasStagedConfig = false;
static std::string eosDeviceToken;
static void *eosSdkHandle = nullptr;
static void *eosConnectLoginAddress = nullptr;

struct EosConnectCredentials
{
    int apiVersion;
    const char *token;
    int type;
};

struct EosConnectLoginOptions
{
    int apiVersion;
    const EosConnectCredentials *credentials;
    const void *userLoginInfo;
};

using eos_connect_login_t = void (*)(
        void *handle,
        const EosConnectLoginOptions *options,
        void *clientData,
        void (*callback)(const void *));

static eos_connect_login_t originalEosConnectLogin = nullptr;

static std::string load_or_create_eos_device_token()
{
    fs::path tokenPath = fs::path(runtimeConfig.appDataDirectory) / "nebula-eos-device-token";
    std::ifstream input(tokenPath);
    std::string token;
    if (input && std::getline(input, token) && token.size() == 40) {
        bool isHex = true;
        for (char character : token) {
            if (!std::isxdigit(static_cast<unsigned char>(character))) {
                isHex = false;
                break;
            }
        }
        if (isHex) {
            return token;
        }
    }

    std::random_device random;
    std::ostringstream value;
    // itch.io OAuth access tokens are represented as opaque 40-character
    // hexadecimal values. AuthFix identifies its placeholder as ItchioKey, so
    // keep the replacement indistinguishable in shape from that credential.
    for (int i = 0; i < 20; ++i) {
        value << std::hex << std::setw(2) << std::setfill('0') << (random() & 0xff);
    }
    token = value.str();

    std::ofstream output(tokenPath, std::ios::trunc);
    output << token;
    return token;
}

static void eos_connect_login_hook(
        void *handle,
        const EosConnectLoginOptions *options,
        void *clientData,
        void (*callback)(const void *))
{
    if (!originalEosConnectLogin || !options) {
        return;
    }

    EosConnectLoginOptions rewrittenOptions = *options;
    EosConnectCredentials rewrittenCredentials{};
    const char *incomingToken = options->credentials == nullptr
            ? nullptr
            : options->credentials->token;
    // A null token is valid for EOS' DeviceId login. Only AuthFix's explicit
    // placeholder may be substituted; rewriting null breaks anonymous login.
    if (incomingToken != nullptr && std::strcmp(incomingToken, "DUMMY") == 0) {
        rewrittenCredentials.apiVersion = 1;
        rewrittenCredentials.token = eosDeviceToken.c_str();
        rewrittenCredentials.type = 15; // EOS_ECT_ITCHIO_KEY
        rewrittenOptions.apiVersion = 2;
        rewrittenOptions.credentials = &rewrittenCredentials;
        rewrittenOptions.userLoginInfo = nullptr;
        options = &rewrittenOptions;
        log_format(LogLevel::INFO, TAG,
                   "Rewrote EOS_Connect_Login placeholder with stable Android device credential.");
    }

    originalEosConnectLogin(handle, options, clientData, callback);
}

static void install_eos_connect_login_hook()
{
    if (originalEosConnectLogin) {
        return;
    }

    // Among Us loads EOS in the game's linker namespace, so Android's
    // dlsym(RTLD_DEFAULT, ...) cannot see it from the injected Fusion library.
    // xDL resolves the already-mapped ELF directly across linker namespaces.
    eosSdkHandle = xdl_open("libEOSSDK.so", XDL_DEFAULT);
    if (!eosSdkHandle) {
        log(LogLevel::ERROR, TAG, "Failed to open the mapped libEOSSDK.so with xDL.");
        return;
    }

    eosConnectLoginAddress = xdl_sym(eosSdkHandle, "EOS_Connect_Login", nullptr);
    if (!eosConnectLoginAddress) {
        eosConnectLoginAddress = xdl_dsym(eosSdkHandle, "EOS_Connect_Login", nullptr);
    }
    if (!eosConnectLoginAddress) {
        log(LogLevel::ERROR, TAG, "Failed to find EOS_Connect_Login in mapped libEOSSDK.so.");
        return;
    }

    eosDeviceToken = load_or_create_eos_device_token();
    int result = DobbyHook(
            eosConnectLoginAddress,
            reinterpret_cast<dobby_dummy_func_t>(eos_connect_login_hook),
            reinterpret_cast<dobby_dummy_func_t *>(&originalEosConnectLogin));
    if (result != 0) {
        originalEosConnectLogin = nullptr;
        log_format(LogLevel::ERROR, TAG, "Failed to hook EOS_Connect_Login: {:d}", result);
        return;
    }
    log(LogLevel::INFO, TAG, "Hooked EOS_Connect_Login for Android AuthFix token substitution.");
}

static std::unordered_map<std::string, std::string> read_key_value_file(const char *configPath)
{
    std::unordered_map<std::string, std::string> values;
    std::ifstream input(configPath);
    std::string line;

    while (std::getline(input, line)) {
        if (line.empty()) {
            continue;
        }

        size_t split = line.find('=');
        if (split == std::string::npos) {
            continue;
        }

        std::string key = line.substr(0, split);
        std::string value = line.substr(split + 1);
        values[key] = value;
    }

    return values;
}

static bool parse_bool_value(const std::string &value)
{
    return value == "1" || value == "true" || value == "TRUE";
}

static bool parse_fusion_config_from_file(const char *configPath, FusionConfig *config)
{
    auto values = read_key_value_file(configPath);
    if (values.empty()) {
        log_format(LogLevel::ERROR, TAG, "Config file is empty or unreadable: {}", configPath);
        return false;
    }

    config->gameLibraryDirectory = values["gameLibraryDirectory"];
    config->appLibraryDirectory = values["appLibraryDirectory"];
    config->appDataDirectory = values["appDataDirectory"];
    config->bepInExDirectory = values["bepInExDirectory"];
    config->dotnetDirectory = values["dotnetDirectory"];
    config->unityDataDirectory = values["unityDataDirectory"];
    config->unityVersion = values["unityVersion"];
    config->useOriginalLibUnity = parse_bool_value(values["useOriginalLibUnity"]);

    if (config->gameLibraryDirectory.empty() || config->appDataDirectory.empty()) {
        log_format(LogLevel::ERROR, TAG, "Invalid config file (missing required fields): {}", configPath);
        return false;
    }

    config->initialized = true;
    return true;
}

static bool stage_fusion_config(const FusionConfig &parsedConfig)
{
    fusion_print_config(parsedConfig);

    fs::path gameLibsPath(parsedConfig.gameLibraryDirectory);
    fs::path appDataPath(parsedConfig.appDataDirectory);

    fs::path libIl2Cpp = gameLibsPath / "libil2cpp.so";
    fs::path libUnity;

    if (parsedConfig.useOriginalLibUnity)
    {
        libUnity = gameLibsPath / "libunity.so";
    }
    else
    {
        libUnity = appDataPath / "libunity.so";
    }

    std::string libUnityPath = libUnity.string();
    try_hook_libunity(libUnityPath, (gameLibsPath / "libunity.so").string());

    fs::path patchedLibIl2Cpp = appDataPath / "libil2cpp.so";
    allocate_setup_injected(libIl2Cpp.c_str(), patchedLibIl2Cpp.c_str(), 1024 * 1024);

    std::string patchedPath = patchedLibIl2Cpp.string();
    libmain_set_override_il2cpp_path(patchedPath.c_str());
    libmain_set_override_unity_path(libUnityPath.c_str());

    {
        std::lock_guard<std::mutex> guard(stageMutex);
        stagedConfig = parsedConfig;
        stagedPatchedIl2CppPath = patchedPath;
        hasStagedConfig = true;
    }

    log(LogLevel::INFO, TAG, "FusionCore config staged successfully.");
    return true;
}

int il2cpp_init_hook(char *domain_name)
{
    log_format(LogLevel::INFO, TAG, "il2cpp_init called with domain: {}", domain_name);
    il2cpp_destroy_init_hook();

    // call the original il2cpp_init function
    int result = il2cpp_init(domain_name);

    if (runtimeConfig.initialized)
    {
        install_eos_connect_login_hook();
        // setup environment variables
        setenv("BEPINEX_GAME_ASSEMBLY_PATH", libmain_get_override_il2cpp_path(), 1);
        setenv("FUSION_BEPINEX_PATH", runtimeConfig.bepInExDirectory.c_str(), 1);
        setenv("FUSION_GAME_BINARY", libmain_get_override_il2cpp_path(), 1);
        setenv("FUSION_GAME_DATA_DIR", runtimeConfig.unityDataDirectory.c_str(), 1);
        setenv("FUSION_APP_DATA_DIR", runtimeConfig.appDataDirectory.c_str(), 1);
        setenv("FUSION_UNITY_VERSION", runtimeConfig.unityVersion.c_str(), 1);

        const char *ssl_cert_path = "/apex/com.android.conscrypt/cacerts";
        const char *backup_cert_path = "/system/etc/security/cacerts";
        if (access(ssl_cert_path, R_OK) == 0) {
            setenv("SSL_CERT_DIR", ssl_cert_path, 1);
        } else if (access(backup_cert_path, R_OK) == 0) {
            setenv("SSL_CERT_DIR", backup_cert_path, 1);
        } else {
            log(LogLevel::WARN, TAG, "No readable SSL cert file found; HTTPS requests may fail.");
        }
        log_format(LogLevel::INFO, TAG, "Using {} for SSL certificates", getenv("SSL_CERT_DIR"));

        fs::path bepInExCoreDirectory = fs::path(runtimeConfig.bepInExDirectory) / "core";

        DotNetConfig dotNetConfig;
        dotNetConfig.runtimeDir = runtimeConfig.dotnetDirectory;
        dotNetConfig.managedLibsDir = bepInExCoreDirectory.string();
        dotNetConfig.entryPointAssembly = "BepInEx.Unity.IL2CPP";
        dotNetConfig.entryPointType = "BepInEx.Unity.IL2CPP.FusionCoreEntrypoint";
        dotNetConfig.entryPointMethod = "Start";

        // set TMPDIR for MonoMod lib drops
        setenv("TMPDIR", runtimeConfig.appDataDirectory.c_str(), 1);

        // execute the managed assembly
        dotnet_execute_assembly(dotNetConfig);
    }
    else
    {
        log(LogLevel::WARN, TAG, "FusionConfig not initialized. Skipping modloader initialization.");
    }

    log_format(LogLevel::INFO, TAG, "il2cpp_init returned: {}", result);
    return result;
}

extern "C" bool fusion_stage_from_config_path(const char *configPath)
{
    if (!configPath) {
        log(LogLevel::ERROR, TAG, "fusion_stage_from_config_path called with null path");
        return false;
    }

    log_format(LogLevel::INFO, TAG, "Staging FusionCore config from {}", configPath);
    FusionConfig parsedConfig{};
    if (!parse_fusion_config_from_file(configPath, &parsedConfig)) {
        return false;
    }

    return stage_fusion_config(parsedConfig);
}

extern "C" bool fusion_bootstrap_from_libmain(JNIEnv *env)
{
    (void) env;

    FusionConfig configToRun;
    std::string patchedIl2CppPath;
    {
        std::lock_guard<std::mutex> guard(stageMutex);
        if (!hasStagedConfig)
        {
            log(LogLevel::ERROR, TAG, "No staged FusionConfig available; cannot bootstrap from libmain namespace.");
            return false;
        }

        configToRun = stagedConfig;
        patchedIl2CppPath = stagedPatchedIl2CppPath;
    }

    runtimeConfig = configToRun;

    log(LogLevel::INFO, TAG, "Executing Fusion bootstrap from libmain namespace...");

    if (!il2cpp_initialize(patchedIl2CppPath.c_str()))
    {
        log_format(LogLevel::ERROR, TAG, "Failed to initialize il2cpp with path: {}", patchedIl2CppPath);
        return false;
    }

    auto library_size = reinterpret_cast<size_t>(get_injected_pool_base() - il2cpp_get_library_base());
    if (!safehook_initialize(il2cpp_get_handle(), il2cpp_get_library_base(), library_size, allocate_injected))
    {
        log(LogLevel::ERROR, TAG, "Failed to initialize SafeHook");
        return false;
    }

    log(LogLevel::INFO, TAG, "Installing il2cpp hooks...");
    il2cpp_install_init_hook(il2cpp_init_hook);
    log(LogLevel::INFO, TAG, "il2cpp hooks installed successfully!");
    log(LogLevel::INFO, TAG, "FusionCore bootstrap finished successfully.");
    return true;
}

