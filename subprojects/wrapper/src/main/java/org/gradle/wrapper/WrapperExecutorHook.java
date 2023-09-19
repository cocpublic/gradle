package org.gradle.wrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WrapperExecutorHook {
    public static final String KEY_CONFIG_FILE = "configFile";
    public static final String JENKINS_DISTRIBUTION_URL = "jenkinsDistributionUrl";
    public static final String KEY_DISTRIBUTION_URL = "distributionUrl";

    /**
     * 一、jenkins环境
     * 如果jenkinsDistributionUrl有值，就是jenkinsDistributionUrl，否则就是distributionUrl
     *
     * 二、本地环境
     * 会先读取configFile指定的文件中是否有值 distributionUrl,新版是.json,旧版是.properties
     * 1、.json文件，读取distributionUrl
     * 2、.properties文件，读取distributionUrl
     *
    */
    public static String modifyDistributionUrlByNeed(String originDistributionUrl, Properties wrapperProperties, File wrapperPropertiesFile) {
        boolean isJenkins  = System.getenv("JENKINS_URL") != null;
        System.err.println("WrapperExecutorHook isJenkins = "+isJenkins);
        if (isJenkins){
            String jenkinsDistributionUrl = getJenkinsDistributionUrl(wrapperProperties);
            if (isGradleZipUrl(jenkinsDistributionUrl)) {
                return jenkinsDistributionUrl;
            }else {
                return originDistributionUrl;
            }
        }
        //本地环境
        String result = originDistributionUrl;
        try {

            String modifyDistributionUrl = getModifyDistributionUrl(wrapperProperties, wrapperPropertiesFile);
            if (isGradleZipUrl(modifyDistributionUrl)) {
                result = modifyDistributionUrl;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static String getModifyDistributionUrl(Properties wrapperProperties, File wrapperPropertiesFile) throws Exception {
        String configFilePath = getProperty(wrapperProperties, KEY_CONFIG_FILE, null);

        if (notEmptyString(configFilePath)) {
            File configFile = new File(rootDir(wrapperPropertiesFile), configFilePath);
            if (configFile.exists()) {
                if (configFilePath.endsWith(".json")){
                    String jsonContent = getContentFromFile(configFile);
                    if (notEmptyString(jsonContent)) {
                        String distributionUrl = getValueInJson(jsonContent, KEY_DISTRIBUTION_URL);
                        if (isGradleZipUrl(distributionUrl)) {
                            return distributionUrl;
                        }
                    }
                } else if (configFilePath.endsWith(".properties")){
                    Properties config = new Properties();
                    loadProperties(configFile,config);
                    String distributionUrl = getProperty(config, KEY_DISTRIBUTION_URL, null);
                    if (isGradleZipUrl(distributionUrl)) {
                        return distributionUrl;
                    }
                }

            }
        }
        return null;
    }

    private static String getJenkinsDistributionUrl(Properties wrapperProperties){
        String jenkinsDistributionUrl = getProperty(wrapperProperties, JENKINS_DISTRIBUTION_URL, null);
        if (isGradleZipUrl(jenkinsDistributionUrl)) {
            return jenkinsDistributionUrl;
        }

        return null;
    }

    private static File rootDir(File propertiesFile) {
        return propertiesFile.getParentFile().getParentFile().getParentFile();
    }

    private static boolean notEmptyString(String text) {
        return text != null && text.trim().length() > 0;
    }

    private static boolean isGradleZipUrl(String url) {
        return notEmptyString(url) && url.startsWith("http") && url.endsWith(".zip");
    }

    private static String getProperty(Properties customProperties, String propertyName, String defaultValue) {
        String value = customProperties.getProperty(propertyName);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    private static void loadProperties(File propertiesFile, Properties properties) throws IOException {
        InputStream inStream = null;
        try {
            inStream = new FileInputStream(propertiesFile);
            properties.load(inStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inStream != null) {
                inStream.close();
            }
        }
    }


    private static String getContentFromFile(File jsonFile) {
        try {
            return new String(Files.readAllBytes(jsonFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String getValueInJson(String jsonContent, String key) {
        String result = "";
        String regex = String.format("\\\"%s\\\":\\\"(\\S+)\\\"", key);
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        try {
            Matcher matcher = pattern.matcher(jsonContent.replace(" ", ""));
            if (matcher.find()) {
                result = matcher.group(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
