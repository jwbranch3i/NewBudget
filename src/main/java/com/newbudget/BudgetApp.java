package com.newbudget;

import com.newbudget.data.BudgetRepository;
import com.newbudget.data.CsvActualImporter;
import com.newbudget.data.Database;
import com.newbudget.service.BudgetService;
import com.newbudget.ui.BudgetViewController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class BudgetApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Database.initialize();
        BudgetRepository repository = new BudgetRepository();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/newbudget/ui/budget-view.fxml"));
        Parent root = loader.load();
        BudgetViewController controller = loader.getController();
        controller.initializeApp(
            stage,
            repository,
            new BudgetService(repository),
            new CsvActualImporter(repository)
        );

        Scene scene = new Scene(root, 1600, 900);
        scene.getStylesheets().add(getClass().getResource("/com/newbudget/ui/styles.css").toExternalForm());
        stage.setTitle("NewBudget");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
