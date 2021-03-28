import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import javax.sound.sampled.*;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GoogleAudioTest {
    private static String previousString = "";
    private static File file = new File("transcription.txt");
    private static long recordingStartTime;

    // A large majority of the below method is from one of Google's samples regarding Cloud Services.
    public static void transcribeAudio(Consumer<String> transcriptHandler) throws Exception {
        ResponseObserver<StreamingRecognizeResponse> responseObserver = null;
        try (SpeechClient client = SpeechClient.create()) {
            // Create listener for Responses and subsequent actions.
            responseObserver = new ResponseObserver<StreamingRecognizeResponse>() {
                // Initialize a list of responses to be filled up later when Cloud Services returns information.
                ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();

                public void onStart(StreamController controller) {
                }

                public void onResponse(StreamingRecognizeResponse response) {
                    responses.add(response);
                }
                public void onError(Throwable t) {
                    System.out.println(t);
                }
                public void onComplete() {
                    // Clear the original file, as the responses should still hold all the information.
                    try {
                        new PrintWriter(file).close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    for (StreamingRecognizeResponse response : responses) {
                        StreamingRecognitionResult result = response.getResultsList().get(0);
                        SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                        String transcript = alternative.getTranscript();
                        transcriptHandler.accept(transcript);
                    }
                }
            };
            // Initializes configurations and connections from the client.
            ClientStream<StreamingRecognizeRequest> clientStream =
                    client.streamingRecognizeCallable().splitCall(responseObserver);
            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setLanguageCode("en-US")
                    .setSampleRateHertz(32000)
                    .build();
            StreamingRecognitionConfig streamingRecognitionConfig =
                    StreamingRecognitionConfig.newBuilder()
                            .setConfig(recognitionConfig)
                            .build();
            StreamingRecognizeRequest request =
                    StreamingRecognizeRequest.newBuilder()
                            .setStreamingConfig(streamingRecognitionConfig)
                            .build();
            clientStream.send(request);
            // Receive input from the microphone and test validity.
            AudioFormat audioFormat =
                    new AudioFormat(32000, 16, 1, true, false);
            DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            // Yes, this is what I mean by test validity.
            if (!AudioSystem.isLineSupported(targetInfo)) {
                System.out.println("nice mic lul jk");
            }
            TargetDataLine targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
            // Keep looping until the program is stopped (work in progress).
            recordingStartTime = System.currentTimeMillis();
            while (true) {
                targetDataLine.open(audioFormat);
                targetDataLine.start();
                System.out.println("Start speaking");
                AudioInputStream audio = new AudioInputStream(targetDataLine);
                long startTime = System.currentTimeMillis();
                // Loop for about five seconds, and then stop and send data to the Cloud Service.
                // Upon receiving a response, the methods above in the listener are called.
                while (true) {
                    targetDataLine.open(audioFormat);
                    targetDataLine.start();
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    byte[] data = new byte[6400];
                    audio.read(data);
                    if (elapsedTime > 5000) {
                        System.out.println("stfu");
                        targetDataLine.stop();
                        targetDataLine.close();
                        break;
                    }
                    request = StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(ByteString.copyFrom(data))
                            .build();
                    clientStream.send(request);
                }
                responseObserver.onComplete();
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static void writeToFile(String text) {
        System.out.println(text);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file,true));
            if (file.createNewFile()) {
                System.out.println("Clean file created!");
            }
            else {
                System.out.println("File already exists, appending...");
                writer.write("--------------------");
                writer.write("\n");
            }
            long timestamp = System.currentTimeMillis() - recordingStartTime;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(timestamp);
            long seconds = TimeUnit.MILLISECONDS.toSeconds((timestamp - (minutes*60000)));
            ArrayList<String> timestampList = new ArrayList<>();
            timestampList.add(minutes + ":" + seconds + ":");
            writer.write(text);
            writer.write("\n");
            writer.close();
            System.out.println("Writing finished!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        transcribeAudio((transcript) -> {
            writeToFile(transcript);
        });
    }

}

