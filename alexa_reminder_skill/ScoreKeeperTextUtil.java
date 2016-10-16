/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package reminder;

import java.util.Arrays;
import java.util.List;

/**
 * Util containing various text related utils.
 */
public final class ReminderTextUtil {

    private ReminderTextUtil() {
    }
    /**
     * Text of complete help.
     */
    public static final String COMPLETE_HELP =
            "Here's some things you can say. did i take medicine?, and exit.";

    /**
     * Text of next help.
     */
    public static final String NEXT_HELP = "You can ask what meds should I take, or say help. What would you like?";

    /**
     * Cleans up the player name, and sanitizes it against the blacklist.
     *
     * @param recognizedPlayerName
     * @return
     */
    public static String getPlayerName(String recognizedPlayerName) {
        if (recognizedPlayerName == null || recognizedPlayerName.isEmpty()) {
            return null;
        }

        String cleanedName;
        if (recognizedPlayerName.contains(" ")) {
            // the name should only contain a first name, so ignore the second part if any
            cleanedName = recognizedPlayerName.substring(recognizedPlayerName.indexOf(" "));
        } else {
            cleanedName = recognizedPlayerName;
        }

        return cleanedName;
    }
}
