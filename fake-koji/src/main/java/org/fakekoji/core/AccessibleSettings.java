/*
 * The MIT License
 *
 * Copyright 2018 .
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.fakekoji.core;

import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.TreeMap;

public class AccessibleSettings {

    private final File dbFileRoot;
    private final File localReposRoot;
    private final File configRoot;
    private final File jenkinsJobsRoot;
    private final File jenkinsJobArchiveRoot;
    private final File scriptsRoot;

    private final int xmlRpcPort;
    private final int fileDownloadPort;
    private final int sshPort;
    private final int jenkinsPort;
    private final int webappPort;
    private URL xmlRpcUrl;
    private URL downloadUrl;
    private URL jenkinsUrlString;
    private ProjectMapping projectMapping;

    public AccessibleSettings(
            File dbFileRoot,
            File localReposRoot,
            File configRoot,
            File jenkinsJobsRoot,
            File jenkinsJobArchiveRoot,
            File scriptsRoot,
            int xmlRpcPort,
            int fileDownloadPort,
            int sshPort,
            int jenkinsPort,
            int webappPort
    ) throws UnknownHostException, MalformedURLException {
        this.dbFileRoot = dbFileRoot;
        this.localReposRoot = localReposRoot;
        this.configRoot = configRoot;
        this.jenkinsJobsRoot = jenkinsJobsRoot;
        this.jenkinsJobArchiveRoot = jenkinsJobArchiveRoot;
        this.scriptsRoot = scriptsRoot;
        this.xmlRpcPort = xmlRpcPort;
        this.fileDownloadPort = fileDownloadPort;
        this.sshPort = sshPort;
        this.jenkinsPort = jenkinsPort;
        this.webappPort = webappPort;
        this.projectMapping = new ProjectMapping(this);

        String thisMachine = InetAddress.getLocalHost().getHostName();
        this.xmlRpcUrl = new URL("http://" + thisMachine + ":" + this.xmlRpcPort + "/RPC2/");
        this.downloadUrl = new URL("http://" + thisMachine + ":" + this.fileDownloadPort + "/");
        this.jenkinsUrlString = new URL("http://" + thisMachine + ":" + jenkinsPort + "/");
    }

    /**
     * Default may lead to localhost, so itcna be desirable to set proper
     * hostname via this method
     *
     * @param specialHost
     * @throws MalformedURLException
     */
    public void setDownloadUrl(String specialHost) throws MalformedURLException {
        this.downloadUrl = new URL("http://" + specialHost + ":" + fileDownloadPort + "/");

    }

    /**
     * Default may lead to localhost, so itcna be desirable to set proper
     * hostname via this method
     *
     * @param specialHost
     * @throws MalformedURLException
     */
    public void setXmlRpcUrl(String specialHost) throws MalformedURLException {
        this.xmlRpcUrl = new URL("http://" + specialHost + ":" + xmlRpcPort + "/RPC2/");
    }

    /**
     * Default may lead to localhost, so itcna be desirable to set proper
     * hostname via this method
     *
     * @param specialHost
     * @throws MalformedURLException
     */
    public void setJekinsUrl(String specialHost) throws MalformedURLException {
        this.jenkinsUrlString = new URL("http://" + specialHost + ":" + jenkinsPort + "/");
    }

    /**
     * @return the dbFileRoot
     */
    public File getDbFileRoot() {
        return dbFileRoot;
    }

    /**
     * @return the localReposRoot
     */
    public File getLocalReposRoot() {
        return localReposRoot;
    }

    public File getConfigRoot() {
        return configRoot;
    }

    public File getJenkinsJobsRoot() {
        return jenkinsJobsRoot;
    }

    public File getJenkinsJobArchiveRoot() {
        return jenkinsJobArchiveRoot;
    }

    public File getScriptsRoot() {
        return scriptsRoot;
    }

    /**
     * @return the xmlRpcPort
     */
    public int getXmlRpcPort() {
        return xmlRpcPort;
    }

    /**
     * @return the fileDownloadPort
     */
    public int getFileDownloadPort() {
        return fileDownloadPort;
    }

    /**
     * @return the sshPort
     */
    public int getSshPort() {
        return sshPort;
    }

    public int getWebappPort() {
        return webappPort;
    }

    public URL getXmlRpcUrl() {
        return xmlRpcUrl;
    }

    public URL getJenkinsUrlString() {
        return jenkinsUrlString;
    }

    public URL getDownloadUrl() {
        return downloadUrl;
    }

    public ProjectMapping getProjectMapping() {
        return projectMapping;
    }

    private String getPublicValues(String property, String value) throws ProjectMappingExceptions.ProjectMappingException {
        TreeMap<String, ResponseContainer.Response> responseTreeMap = new TreeMap<>();

        responseTreeMap.put("repos", new ResponseContainer.GetPathResponse(localReposRoot.getAbsolutePath(), "Path to folder containing repositories"));
        responseTreeMap.put("root", new ResponseContainer.GetPathResponse(dbFileRoot.getAbsolutePath(), "Path to folder containing builds"));
        responseTreeMap.put("xport", new ResponseContainer.GetPortResponse(xmlRpcPort, "XML-RPC port"));
        responseTreeMap.put("dport", new ResponseContainer.GetPortResponse(fileDownloadPort, "Download port"));
        responseTreeMap.put("uport", new ResponseContainer.GetPortResponse(sshPort, "SSH upload port"));
        responseTreeMap.put("allProducts", new ResponseContainer.GetAllProductsResponse(projectMapping));
        responseTreeMap.put("allProjects", new ResponseContainer.GetAllProjectsResponse(projectMapping));
        responseTreeMap.put("projectsOfProduct", new ResponseContainer.GetProjectsOfProductResponse(projectMapping, value));
        responseTreeMap.put("projectOfNvra", new ResponseContainer.GetProjectOfNvraResponse(projectMapping, value));
        responseTreeMap.put("productOfNvra", new ResponseContainer.GetProductOfNvraResponse(projectMapping, value));
        responseTreeMap.put("productOfProject", new ResponseContainer.GetProductOfProjectResponse(projectMapping, value));
        responseTreeMap.put("expectedArchesOfProject", new ResponseContainer.GetExpectedArchesOfProjectResponse(projectMapping, value));
        responseTreeMap.put("expectedArchesOfNvr", new ResponseContainer.GetExpectedArchesOfNVR(projectMapping, value));
        responseTreeMap.put("help", new ResponseContainer.GetHelpResponse(responseTreeMap));

        if (responseTreeMap.get(property) == null) {
            throw new ProjectMappingExceptions.BadRequestException();
        }

        return responseTreeMap.get(property).respond();
    }

    public String get(String property, String value) throws ProjectMappingExceptions.ProjectMappingException {
        return getPublicValues(property, value);
    }
}
