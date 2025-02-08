package com.niine.serverless.apiservice;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException; 
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;



public class StreamLambdaHandler implements RequestStreamHandler {

    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            // Initialize the Spring Boot Lambda handler for the     app (Application.class)
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(Application.class);
        } catch (Exception e) {
            // Log initialization failure and rethrow as a runtime exception
            e.printStackTrace();
            throw new RuntimeException("Could not initialize Spring Boot application", e);
        }
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        // Use ByteArrayOutputStream to capture the response data from Lambda container
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Handle the request using Spring Boot Lambda handler
        handler.proxyStream(inputStream, byteArrayOutputStream, context);

        // Convert the response output stream to a string (in UTF-8)
        String response = byteArrayOutputStream.toString(StandardCharsets.UTF_8);

        // Optionally log the response for debugging purposes (use structured logging in production)
        System.out.println("Response: " + response);

        // Write the response back to the original output stream
        outputStream.write(response.getBytes(StandardCharsets.UTF_8));
    }
}
