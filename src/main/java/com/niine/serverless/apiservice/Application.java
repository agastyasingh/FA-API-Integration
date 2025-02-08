package com.niine.serverless.apiservice;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.niine.serverless.apiservice.controller.PingController;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;



@SpringBootApplication
// We use direct @Import instead of @ComponentScan to speed up cold starts
// @ComponentScan(basePackages = "com.niine.serverless.controller")

@Import({ com.niine.serverless.apiservice.controller.PingController.class })
@RestController
@RequestMapping("/apiservice")
public class Application {
	
	@Autowired
	private RestTemplate restTemplate;
	
	private String AccessKey = "";
	private String SecretAccessKey = "";
	private String bucketName = "";
	
	
    ///Dependency Injection
	ObjectMapper objectMapper;
    
    public Application(ObjectMapper objectMapper) {
  	  this.objectMapper = objectMapper;
    }
  
    
    ///API Endpoint to fetch data from FA Api    
    @GetMapping("/externalApi")
    public String getExternalApiResponse() throws Exception {
        String lastVisitId = readVisitIdFromS3();
      String prevlastVisitId = lastVisitId;
        String username = "Niine_10543";
        String password = "BJanftQsvsWP6V45Apg_";
        
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        
        String authHeader = "Basic " + encodedAuth;
    	int counter=0;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        

        boolean isThereMoreData = true;
        int requestCount = 0;
        int retryCount = 0;

        // Path to save the JSON file in the Lambda's /tmp directory
        File jsonFile = new File("/tmp/api-output-data.json");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT); 

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile))) {
            writer.write("[\n");  // Start of JSON array

            
//            String Date = readDateFromS3();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate currentDate = LocalDate.now();
            LocalDate previousDate = currentDate.minusDays(1);
            String Date = previousDate.format(formatter);
            

            
            while (isThereMoreData && requestCount < 250 && retryCount < 5) {
                String uri = "https://api.fieldassist.in/api/V3/Visit/detailedVisit?lastVisitId=" + lastVisitId + "&includeUnproductive=true";
                System.out.println("Sending request with lastVisitId: " + lastVisitId); // Log URI
                
                ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

                try {
                    List<Map<String, Object>> records = objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
                    
                    if(prevlastVisitId == lastVisitId) {
                    	counter++;
                    	System.out.println("counter value = " + counter);
                    }

                    if (records.isEmpty() || counter>2) {
                        isThereMoreData = false;
                    } else {
                        for (Map<String, Object> record : records) {
                            // Extract and parse the Time field
                            String timeValue = (String) record.get("Time");
                            if (timeValue != null && timeValue.startsWith(Date)) { // Check if the date matches current Date
                                // Flatten each record if needed
                                ObjectNode flatRecord = objectMapper.convertValue(record, ObjectNode.class);

                                // Write each filtered record to the output JSON file
                                writer.write(objectMapper.writeValueAsString(flatRecord) + ",\n");

                                // Update lastVisitId for the next page
                                Object visitIdObj = record.get("VisitId");
                                if (visitIdObj instanceof Integer) {
                                    lastVisitId = String.valueOf(((Integer) visitIdObj).longValue());
                                } else if (visitIdObj instanceof Long) {
                                    lastVisitId = String.valueOf((Long) visitIdObj);
                                }
                                System.out.println("Updated lastVisitId to: " + lastVisitId); // Log the updated value
                            }
                        }
                        requestCount++;
                        
                        if(lastVisitId != prevlastVisitId) {
                        	prevlastVisitId = lastVisitId;
                        	counter=0;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    retryCount++;
                    Thread.sleep(2000);
                }
            }


  ////////////////////////////////////////////////////////////////////////////////////////////////          
            writer.write("]");
        }

        // Upload to S3 Method Call
        uploadFileToS3(jsonFile);
        
        LocalTime currentTime = LocalTime.now();
        LocalTime cutoffTime = LocalTime.of(23, 0); // 11:00 PM

        // Check if current time is greater than 11:00 PM
        if (currentTime.isAfter(cutoffTime)) {
            // Update LastVisitId in id.txt (in S3 bucket)
            writeVisitIdToS3(lastVisitId);
            System.out.println("LastVisitId updated in S3 as the current time is after 11:00 PM.");
        } else {
            System.out.println("Skipped updating LastVisitId to S3 as the current time is before 11:00 PM.");
        }
        
//        updateDateInS3();
        

        return "JSON file created and uploaded successfully to S3 and Last Visit Id has been updated";
        
    }


  
    
    
    // JSON Output file to S3
    private void uploadFileToS3(File file) {

    	 AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AccessKey, SecretAccessKey);
    	 S3Client s3 = S3Client.builder()
    			.region(Region.AP_SOUTH_1)
    			.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
    			.build();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)  
                .key("api-output-data.json")
                .build();

        try {
            s3.putObject(putObjectRequest, RequestBody.fromFile(file));
            System.out.println("File uploaded successfully to S3");
        } catch (S3Exception e) {
            e.printStackTrace();
        } finally {
            s3.close();
        }
    }
    
    
///////////////////////          READ FROM S3 - METHODS           //////////////////////////////
   
    //read VisitId from id.txt in S3
    private String readVisitIdFromS3() {

    	 AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AccessKey, SecretAccessKey);
    	 S3Client s3 = S3Client.builder()
    			.region(Region.AP_SOUTH_1)
    			.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
    			.build();
        String visitId = null;
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key("id.txt")
                .build();

        try {
            ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(getObjectRequest);
            String content = objectBytes.asString(StandardCharsets.UTF_8);

            for (String line : content.split("\n")) {
                if (line.startsWith("VisitId=")) {
                    visitId = line.split("=")[1].trim();
                    break;
                }
            }

            System.out.println("VisitId: " + visitId);
        } catch (S3Exception e) {
            e.printStackTrace();
        } finally {
            s3.close();
        }

        return visitId;
    }
    
    
 //read Date from date.txt in S3
    private String readDateFromS3() {

        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AccessKey, SecretAccessKey);
        S3Client s3 = S3Client.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
        String date = null;
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key("date.txt")
                .build();

        try {
            ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(getObjectRequest);
            String content = objectBytes.asString(StandardCharsets.UTF_8);

            for (String line : content.split("\n")) {
                if (line.startsWith("Date=")) {
                    date = line.split("=")[1].trim();
                    break;
                }
            }

            System.out.println("Date: " + date);
        } catch (S3Exception e) {
            e.printStackTrace();
        } finally {
            s3.close();
        }

        return date;
    }

    
    
///////////////////////            WRITE TO S3 - METHODS             //////////////////////////////

    
/////update lastVisitId
    private void writeVisitIdToS3(String lastVisitId) {
    	
    	 // S3 Client Build and Details
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AccessKey, SecretAccessKey);
        S3Client s3 = S3Client.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    	
    	try {
    	GetObjectRequest getObjectRequest = GetObjectRequest.builder()
    			.bucket(bucketName)
    			.key("id.txt")
    			.build();
    	
    	PutObjectRequest putObjectRequest = PutObjectRequest.builder()
    			.bucket(bucketName)
    			.key("id.txt")
    			.build();
    	
    	ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(getObjectRequest);
    	String content = objectBytes.asString(StandardCharsets.UTF_8);
    	
    	///update VisitId value
    	String updatedContent = "VisitId=" + lastVisitId;
    	
		Path tempFilePath = Files.createTempFile("updated-id", ".txt");
		Files.write(tempFilePath, updatedContent.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
			
	    s3.putObject(putObjectRequest, RequestBody.fromFile(tempFilePath.toFile()));
        System.out.println("File updated successfully in S3 with new VisitId: " + lastVisitId);
    	
        }catch(Exception e) {
        	e.printStackTrace();
        }finally {
        	s3.close();
        }
    }
    
    
/////update Date
    private void updateDateInS3() {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(AccessKey, SecretAccessKey);
        S3Client s3 = S3Client.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();

        try {
            // Get the system's current date
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate currentDate = LocalDate.now();

            // Subtract one day from the current date
            LocalDate previousDate = currentDate.minusDays(1);
            String updatedDate = "Date=" + previousDate.format(formatter);

            // Update date.txt file in S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key("date.txt")
                    .build();

            s3.putObject(putObjectRequest, RequestBody.fromString(updatedDate, StandardCharsets.UTF_8));
            System.out.println("Date successfully updated to: " + updatedDate);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            s3.close();
        }
    }

    
	public static void main(String[] args) {
		SpringApplication.run(Application.class,args);
	}
}
	