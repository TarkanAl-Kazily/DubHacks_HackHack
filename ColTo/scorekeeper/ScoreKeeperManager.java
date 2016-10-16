/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package medtracker;

import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

import medtracker.storage.MedTrackerDao;
import medtracker.storage.MedTrackerDynamoDbClient;
import medtracker.storage.MedTrackerPatient;
import medtracker.storage.MedTrackerPatientData;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.Card;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;

/**
 * The {@link MedTrackerManager} receives various events and intents and manages the flow of the
 * patient.
 */
public class MedTrackerManager {
    /**
     * Intent slot for User name.
     */
    private static final String SLOT_USER_NAME = "UserName";

    /**
     * PROBABLY NEED TO HOLD MEDS AS A FIELD IN A LIST OF SOME FORM
     */

    private final MedTrackerDao medTrackerDao;

    public MedTrackerManager(final AmazonDynamoDBClient amazonDynamoDbClient) {
        MedTrackerDynamoDbClient dynamoDbClient = new MedTrackerDynamoDbClient(amazonDynamoDbClient);
        medTrackerDao = new MedTrackerDao(dynamoDbClient);
    }

    /**
     * Creates and returns response for Launch request.
     *
     * @param request
     *            {@link LaunchRequest} for this request
     * @param session
     *            Speechlet {@link Session} for this request
     * @return response for launch request
     */
    public SpeechletResponse getLaunchResponse(LaunchRequest request, Session session) {
        // Speak welcome message and ask user questions
        // based on whether there are players or not.
        String speechText, repromptText;
        MedTrackerPatient patient = medTrackerDao.getMedTrackerPatient(session);

        // DO SET UP IF FIRST TIME 
        if (patient == null) {
            speechText = "This is MedTracker, What is your name?";
            repromptText = "Please tell me what your name is?";
            
        //GET MESSAGES ON LAUNCH TO GO HERE    
        } else {	
            speechText = "MedTracker, What can I do for you?";
            repromptText = MedTrackerTextUtil.NEXT_HELP;
        }

        return getAskSpeechletResponse(speechText, repromptText);
    }

    /**
     * Creates and returns response for the new patient intent.
     *
     * @param session
     *            {@link Session} for the request
     * @param skillContext
     *            {@link SkillContext} for this request
     * @return response for the new patient intent.
     */
    public SpeechletResponse getNewPatientIntentResponse(Session session, SkillContext skillContext) {
        MedTrackerPatient patient = medTrackerDao.getMedTrackerPatient(session);

        if (patient == null) {
            return getAskSpeechletResponse("New patient started. Who's your first player?",
                    "Please tell me who\'s your first player?");
        }

        // Reset current patient
        patient.resetScores();
        medTrackerDao.saveMedTrackerPatient(patient);

        String speechText =
                "New patient started with " + patient.getNumberOfPlayers() + " existing player"
                        + (patient.getNumberOfPlayers() != 1 ? "" : "s") + ".";

        if (skillContext.needsMoreHelp()) {
            String repromptText =
                    "You can give a player points, add another player, reset all players or "
                            + "exit. What would you like?";
            speechText += repromptText;
            return getAskSpeechletResponse(speechText, repromptText);
        } else {
            return getTellSpeechletResponse(speechText);
        }
    }

    /**
     * Creates and returns response for the add player intent.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            Speechlet {@link Session} for this request
     * @param skillContext
     * @return response for the add player intent.
     */
    public SpeechletResponse getAddPlayerIntentResponse(Intent intent, Session session,
            SkillContext skillContext) {
        // add a player to the current patient,
        // terminate or continue the conversation based on whether the intent
        // is from a one shot command or not.
        String newPlayerName =
                MedTrackerTextUtil.getPlayerName(intent.getSlot(SLOT_PLAYER_NAME).getValue());
        if (newPlayerName == null) {
            String speechText = "OK. Who do you want to add?";
            return getAskSpeechletResponse(speechText, speechText);
        }

        // Load the previous patient
        MedTrackerPatient patient = medTrackerDao.getMedTrackerPatient(session);
        if (patient == null) {
            patient = MedTrackerPatient.newInstance(session, MedTrackerPatientData.newInstance());
        }

        patient.addPlayer(newPlayerName);

        // Save the updated patient
        medTrackerDao.saveMedTrackerPatient(patient);

        String speechText = newPlayerName + " has joined your patient. ";
        String repromptText = null;

        if (skillContext.needsMoreHelp()) {
            if (patient.getNumberOfPlayers() == 1) {
                speechText += "You can say, I am done adding players. Now who's your next player?";

            } else {
                speechText += "Who is your next player?";
            }
            repromptText = MedTrackerTextUtil.NEXT_HELP;
        }

        if (repromptText != null) {
            return getAskSpeechletResponse(speechText, repromptText);
        } else {
            return getTellSpeechletResponse(speechText);
        }
    }

    /**
     * Creates and returns response for the add score intent.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @param skillContext
     *            {@link SkillContext} for this request
     * @return response for the add score intent
     */
    public SpeechletResponse getAddScoreIntentResponse(Intent intent, Session session,
            SkillContext skillContext) {
        String playerName =
                MedTrackerTextUtil.getPlayerName(intent.getSlot(SLOT_PLAYER_NAME).getValue());
        if (playerName == null) {
            String speechText = "Sorry, I did not hear the player name. Please say again?";
            return getAskSpeechletResponse(speechText, speechText);
        }

        int score = 0;
        try {
            score = Integer.parseInt(intent.getSlot(SLOT_SCORE_NUMBER).getValue());
        } catch (NumberFormatException e) {
            String speechText = "Sorry, I did not hear the points. Please say again?";
            return getAskSpeechletResponse(speechText, speechText);
        }

        MedTrackerPatient patient = medTrackerDao.getMedTrackerPatient(session);
        if (patient == null) {
            return getTellSpeechletResponse("A patient has not been started. Please say New Patient to "
                    + "start a new patient before adding scores.");
        }
            return getAskSpeechletResponse(speechText, speechText);
        }

        // Update score
        if (!patient.addScoreForPlayer(playerName, score)) {
            String speechText = "Sorry, " + playerName + " has not joined the patient. What else?";
            return getAskSpeechletResponse(speechText, speechText);
        }

        // Save patient
        medTrackerDao.saveMedTrackerPatient(patient);

        // Prepare speech text. If the patient has less than 3 players, skip reading scores for each
        // player for brevity.
        String speechText = score + " for " + playerName + ". ";

        if (patient.getNumberOfPlayers() == 0) {
            String speechText = "Sorry, no player has joined the patient yet. What can I do for you?";
        if (patient.getNumberOfPlayers() > MAX_PLAYERS_FOR_SPEECH) {
            speechText += playerName + " has " + patient.getScoreForPlayer(playerName) + " in total.";
        } else {
            speechText += getAllScoresAsSpeechText(patient.getAllScoresInDescndingOrder());
        }

        return getTellSpeechletResponse(speechText);
    }

    /**
     * Creates and returns response for the tell scores intent.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @return response for the tell scores intent
     */
    public SpeechletResponse getTellScoresIntentResponse(Intent intent, Session session) {
        // tells the scores in the leaderboard and send the result in card.
        MedTrackerPatient patient = medTrackerDao.getMedTrackerPatient(session);

        if (patient == null || !patient.hasPlayers()) {
            return getTellSpeechletResponse("Nobody has joined the patient.");
        }

        SortedMap<String, Long> sortedScores = patient.getAllScoresInDescndingOrder();
        String speechText = getAllScoresAsSpeechText(sortedScores);
        Card leaderboardScoreCard = getLeaderboardScoreCard(sortedScores);

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        return SpeechletResponse.newTellResponse(speech, leaderboardScoreCard);
    }

    /**
     * Creates and returns response for the reset players intent.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @return response for the reset players intent
     */
    public SpeechletResponse getResetPlayersIntentResponse(Intent intent, Session session) {
        // Remove all players
        MedTrackerPatient patient =
                MedTrackerPatient.newInstance(session, MedTrackerPatientData.newInstance());
        medTrackerDao.saveMedTrackerPatient(patient);

        String speechText = "New patient started without players. Who do you want to add first?";
        return getAskSpeechletResponse(speechText, speechText);
    }

    /**
     * Creates and returns response for the help intent.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @param skillContext
     *            {@link SkillContext} for this request
     * @return response for the help intent
     */
    public SpeechletResponse getHelpIntentResponse(Intent intent, Session session,
            SkillContext skillContext) {
        return skillContext.needsMoreHelp() ? getAskSpeechletResponse(
                MedTrackerTextUtil.COMPLETE_HELP + " So, how can I help?",
                MedTrackerTextUtil.NEXT_HELP)
                : getTellSpeechletResponse(MedTrackerTextUtil.COMPLETE_HELP);
    }

    /**
     * Creates and returns response for the exit intent.
     *
     * @param intent
     *            {@link Intent} for this request
     * @param session
     *            {@link Session} for this request
     * @param skillContext
     *            {@link SkillContext} for this request
     * @return response for the exit intent
     */
    public SpeechletResponse getExitIntentResponse(Intent intent, Session session,
            SkillContext skillContext) {
        return skillContext.needsMoreHelp() ? getTellSpeechletResponse("Okay. Whenever you're "
                + "ready, you can start giving points to the players in your patient.")
                : getTellSpeechletResponse("");
    }

    /**
     * Returns an ask Speechlet response for a speech and reprompt text.
     *
     * @param speechText
     *            Text for speech output
     * @param repromptText
     *            Text for reprompt output
     * @return ask Speechlet response for a speech and reprompt text
     */
    private SpeechletResponse getAskSpeechletResponse(String speechText, String repromptText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Session");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText(repromptText);
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptSpeech);

        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    /**
     * Returns a tell Speechlet response for a speech and reprompt text.
     *
     * @param speechText
     *            Text for speech output
     * @return a tell Speechlet response for a speech and reprompt text
     */
    private SpeechletResponse getTellSpeechletResponse(String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("Session");
        card.setContent(speechText);

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        return SpeechletResponse.newTellResponse(speech, card);
    }

    /**
     * Converts a {@link Map} of scores into text for speech. The order of the entries in the text
     * is determined by the order of entries in {@link Map#entrySet()}.
     *
     * @param scores
     *            A {@link Map} of scores
     * @return a speech ready text containing scores
     */
    private String getAllScoresAsSpeechText(Map<String, Long> scores) {
        StringBuilder speechText = new StringBuilder();
        int index = 0;
        for (Entry<String, Long> entry : scores.entrySet()) {
            if (scores.size() > 1 && index == scores.size() - 1) {
                speechText.append(" and ");
            }
            String singularOrPluralPoints = entry.getValue() == 1 ? " point, " : " points, ";
            speechText
                    .append(entry.getKey())
                    .append(" has ")
                    .append(entry.getValue())
                    .append(singularOrPluralPoints);
            index++;
        }

        return speechText.toString();
    }

    /**
     * Creates and returns a {@link Card} with a formatted text containing all scores in the patient.
     * The order of the entries in the text is determined by the order of entries in
     * {@link Map#entrySet()}.
     *
     * @param scores
     *            A {@link Map} of scores
     * @return leaderboard text containing all scores in the patient
     */
    private Card getLeaderboardScoreCard(Map<String, Long> scores) {
        StringBuilder leaderboard = new StringBuilder();
        int index = 0;
        for (Entry<String, Long> entry : scores.entrySet()) {
            index++;
            leaderboard
                    .append("No. ")
                    .append(index)
                    .append(" - ")
                    .append(entry.getKey())
                    .append(" : ")
                    .append(entry.getValue())
                    .append("\n");
        }

        SimpleCard card = new SimpleCard();
        card.setTitle("Leaderboard");
        card.setContent(leaderboard.toString());
        return card;
    }
}
