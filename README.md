# Api-Service serverless API
The apiservice project, created with [`aws-serverless-java-container`](https://github.com/aws/serverless-java-container).

The starter project defines a simple `/ping` resource that can accept `GET` requests with its tests.

The project folder also includes a `template.yml` file. You can use this [SAM](https://github.com/awslabs/serverless-application-model) file to deploy the project to AWS Lambda and Amazon API Gateway or test in local with the [SAM CLI](https://github.com/awslabs/aws-sam-cli). 

Libraries are skipped from upload to git, use **'POM.XML'** or **'Compressed Zip/Jar file'** in target folder to test the service.

## Pre-requisites
* [AWS CLI](https://aws.amazon.com/cli/)
* [SAM CLI](https://github.com/awslabs/aws-sam-cli)
* [Maven](https://maven.apache.org/)

##Pre-requisites to run the project locally
- Java (JDK + IDE)
- Maven (installed globally on your system)
- Spring-Boot for easy build and set-up


## Building the project

**(Recommended)** 

  - Manually change API link in the code (Application.java) to extract data from desired API.                                                   
  - Use Compressed Zip/Jar file with name 'apiservice-0.0.1-SNAPSHOT-lambda-package' to test the service.
  - Go to AWS Lambda , choose runtime as Java-21 and upload the zip file to Code section.
  -  Update runtime settings, enter handler as 'com.niine.serverless.apiservice.StreamLambdaHandler::handleRequest'
  -  Increase timeout settings to allow for service to extract data successfully.


You can use the SAM CLI to quickly build the project
```bash
$ mvn archetype:generate -DartifactId=apiservice -DarchetypeGroupId=com.amazonaws.serverless.archetypes -DarchetypeArtifactId=aws-serverless-jersey-archetype -DarchetypeVersion=2.0.3 -DgroupId=com.niine.serverless -Dversion=0.0.1-SNAPSHOT -Dinteractive=false
$ cd apiservice
$ sam build
Building resource 'ApiserviceFunction'
Running JavaGradleWorkflow:GradleBuild
Running JavaGradleWorkflow:CopyArtifacts

Build Succeeded

Built Artifacts  : .aws-sam/build
Built Template   : .aws-sam/build/template.yaml

Commands you can use next
=========================
[*] Invoke Function: sam local invoke
[*] Deploy: sam deploy --guided
```

## Testing locally with the SAM CLI

From the project root folder - where the `template.yml` file is located - start the API with the SAM CLI.

```bash
$ sam local start-api

...
Mounting com.amazonaws.serverless.archetypes.StreamLambdaHandler::handleRequest (java11) at http://127.0.0.1:3000/{proxy+} [OPTIONS GET HEAD POST PUT DELETE PATCH]
...
```

Using a new shell, you can send a test ping request to your API:

```bash
$ curl -s http://127.0.0.1:3000/ping | python -m json.tool

{
    "pong": "Hello, World!"
}
``` 

##Manual Deployment to AWS Lambda
To deploy the project on AWS Lambda and test:
 1) Go to your project root directory and open command prompt
 2) Run the command 'mvn clean package' (a jar/zip file will be generated in the "target" folder)
 3) Go to Code Section in your Lambda function on AWS Console and upload the jar/zip file generated in step 2.
 4) Edit the runtime settings and enter the Handler as "com.niine.serverless.apiservice.StreamLambdaHandler::handleRequest"
 5) The project is now deployed on AWS Lambda
 
##Test the Lambda function (After project deployment)
- Provide an Event Name in the Test section of the Lambda
- Select 'apigateway-aws-proxy' as the template
- Edit following values in Event JSON :
  1) path to "/apiservice/externalApi"
  2) httpMethod to "GET"
  3) isBase64Encoded to "false"
- Click on Test


## Deploying to AWS
To deploy the application in your AWS account, you can use the SAM CLI's guided deployment process and follow the instructions on the screen

```
$ sam deploy --guided
```

Once the deployment is completed, the SAM CLI will print out the stack's outputs, including the new application URL. You can use `curl` or a web browser to make a call to the URL

```
...
-------------------------------------------------------------------------------------------------------------
OutputKey-Description                        OutputValue
-------------------------------------------------------------------------------------------------------------
ApiserviceApi - URL for application            https://xxxxxxxxxx.execute-api.us-west-2.amazonaws.com/Prod/pets
-------------------------------------------------------------------------------------------------------------
```

Copy the `OutputValue` into a browser or use curl to test your first request:

```bash
$ curl -s https://xxxxxxx.execute-api.us-west-2.amazonaws.com/Prod/ping | python -m json.tool

{
    "pong": "Hello, World!"
}
```
