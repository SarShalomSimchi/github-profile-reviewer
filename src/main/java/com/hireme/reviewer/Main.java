package com.hireme.reviewer;

import com.hireme.reviewer.app.ApplicationComponents;
import com.hireme.reviewer.config.AppConfig;
import com.hireme.reviewer.exception.ApplicationException;
import com.hireme.reviewer.exception.InvalidInputException;
import com.hireme.reviewer.input.GitHubInputParser;

public final class Main {

    private static final int SUCCESS = 0;
    private static final int APPLICATION_ERROR = 1;
    private static final int INVALID_INPUT = 2;

    private static final String USAGE =
            "Usage: java -jar github-profile-reviewer-1.0.0-all.jar "
                    + "<github-username-or-profile-url>";

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);

        if (exitCode != SUCCESS) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        try {
            String username = GitHubInputParser.parse(args);

            AppConfig config = AppConfig.load();
            config.ai().requireApiKey();

            ApplicationComponents components =
                    ApplicationComponents.create(config);

            var profileReview = components
                    .reviewService()
                    .review(username);

            var reportPath = components
                    .reportWriter()
                    .write(profileReview);

            System.out.println();
            System.out.println("HTML report: " + reportPath);

            return SUCCESS;
        } catch (InvalidInputException exception) {
            System.err.println("Error: " + exception.getMessage());
            System.err.println(USAGE);
            return INVALID_INPUT;
        } catch (ApplicationException exception) {
            System.err.println("Error: " + exception.getMessage());
            return APPLICATION_ERROR;
        }
    }
}