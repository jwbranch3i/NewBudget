package com.newbudget;

import com.newbudget.model.CategoryType;
import com.newbudget.model.MonthSnapshot;
import com.newbudget.data.BudgetRepository;
import com.newbudget.data.CsvActualImporter;
import com.newbudget.data.Database;
import com.newbudget.service.BudgetService;
import com.newbudget.ui.BudgetTableRow;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class BudgetApp extends Application {
    private static final DateTimeFormatter MONTH_DISPLAY = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final BudgetRepository repository = new BudgetRepository();
    private final BudgetService budgetService = new BudgetService(repository);
    private final CsvActualImporter csvActualImporter = new CsvActualImporter(repository);

    private final ObservableList<BudgetTableRow> incomeRows = FXCollections.observableArrayList();
    private final ObservableList<BudgetTableRow> mandatoryRows = FXCollections.observableArrayList();
    private final ObservableList<BudgetTableRow> discretionaryRows = FXCollections.observableArrayList();

    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();
    private final Label statusLabel = new Label("Ready");

    private YearMonth selectedMonth;

    @Override
    public void start(Stage stage) {
        Database.initialize();
        selectedMonth = repository.getLatestMonthWithData().orElse(YearMonth.now());

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setPadding(new Insets(10));
        root.setTop(buildTopBar(stage));
        root.setCenter(buildTables());
        statusLabel.getStyleClass().add("status-label");
        root.setBottom(statusLabel);

        refreshMonths();
        selectMonth(selectedMonth, false);

        Scene scene = new Scene(root, 1400, 850);
        scene.getStylesheets().add(getClass().getResource("/com/newbudget/ui/styles.css").toExternalForm());
        stage.setTitle("NewBudget");
        stage.setScene(scene);
        stage.show();
    }

    private HBox buildTopBar(Stage stage) {
        Label monthLabel = new Label("Month:");
        monthLabel.getStyleClass().add("month-label");

        Button previousMonthButton = new Button("◀ Previous");
        previousMonthButton.getStyleClass().add("month-nav-button");
        previousMonthButton.setOnAction(event -> selectMonth(selectedMonth.minusMonths(1), true));

        Button nextMonthButton = new Button("Next ▶");
        nextMonthButton.getStyleClass().add("month-nav-button");
        nextMonthButton.setOnAction(event -> selectMonth(selectedMonth.plusMonths(1), true));

        monthPicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(YearMonth object) {
                return object == null ? "" : MONTH_DISPLAY.format(object);
            }

            @Override
            public YearMonth fromString(String string) {
                return null;
            }
        });
        monthPicker.setOnAction(event -> {
            YearMonth chosen = monthPicker.getValue();
            if (chosen != null) {
                selectMonth(chosen, false);
            }
        });
        monthPicker.getStyleClass().add("month-picker");

        Button importCsvButton = new Button("Import Actuals CSV");
        importCsvButton.getStyleClass().add("action-button");
        importCsvButton.setOnAction(event -> importCsv(stage));

        Button currentMonthButton = new Button("Use Current Month");
        currentMonthButton.getStyleClass().add("secondary-button");
        currentMonthButton.setOnAction(event -> {
            selectMonth(YearMonth.now(), true);
        });

        HBox bar = new HBox(
            10,
            previousMonthButton,
            nextMonthButton,
            monthLabel,
            monthPicker,
            importCsvButton,
            currentMonthButton
        );
        bar.getStyleClass().add("top-bar");
        bar.setPadding(new Insets(0, 0, 10, 0));
        return bar;
    }

    private VBox buildTables() {
        TableView<BudgetTableRow> incomeTable = createTable(incomeRows, false);

        TableView<BudgetTableRow> mandatoryTable = createTable(
            mandatoryRows,
            true,
            row -> budgetService.updateMonthlyClassification(selectedMonth, row.getCategoryId(), CategoryType.DISCRETIONARY)
        );

        TableView<BudgetTableRow> discretionaryTable = createTable(
            discretionaryRows,
            true,
            row -> budgetService.updateMonthlyClassification(selectedMonth, row.getCategoryId(), CategoryType.MANDATORY)
        );

        VBox container = new VBox(
            12,
            createSectionPane("INCOME", incomeTable),
            createSectionPane("MANDATORY", mandatoryTable),
            createSectionPane("DISCRETIONARY", discretionaryTable)
        );
        container.getStyleClass().add("sections-root");
        VBox.setVgrow(container, Priority.ALWAYS);
        return container;
    }

    private VBox createSectionPane(String title, TableView<BudgetTableRow> table) {
        Label sectionTitle = sectionLabel(title);
        VBox section = new VBox(6, sectionTitle, table);
        section.getStyleClass().add("section-card");
        section.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        return section;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private TableView<BudgetTableRow> createTable(ObservableList<BudgetTableRow> rows, boolean allowMove) {
        return createTable(rows, allowMove, null);
    }

    private TableView<BudgetTableRow> createTable(
        ObservableList<BudgetTableRow> rows,
        boolean allowMove,
        Consumer<BudgetTableRow> moveAction
    ) {
        TableView<BudgetTableRow> table = new TableView<>(rows);
        table.setEditable(true);
        table.getStyleClass().add("budget-table");

        TableColumn<BudgetTableRow, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
            "  ".repeat(Math.max(0, cell.getValue().getDepth())) + cell.getValue().getCategory()
        ));
        categoryColumn.setPrefWidth(420);

        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);

        TableColumn<BudgetTableRow, Number> actualColumn = moneyColumn("Actual Amt", BudgetTableRow::getActualAmount, money);
        TableColumn<BudgetTableRow, Number> differenceColumn = moneyColumn("Difference", BudgetTableRow::getDifference, money);
        TableColumn<BudgetTableRow, Number> balanceColumn = moneyColumn("Balance", BudgetTableRow::getBalance, money);

        TableColumn<BudgetTableRow, Number> budgetColumn = new TableColumn<>("Budget Amt");
        budgetColumn.setCellValueFactory(cell -> cell.getValue().budgetAmountProperty());
        budgetColumn.setCellFactory(TextFieldTableCell.forTableColumn(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                return money.format(object.doubleValue());
            }

            @Override
            public Number fromString(String string) {
                String normalized = string.replace("$", "").replace(",", "").trim();
                if (normalized.isBlank()) {
                    return 0.0;
                }
                return Double.parseDouble(normalized);
            }
        }));
        budgetColumn.setOnEditCommit(event -> {
            BudgetTableRow row = event.getRowValue();
            double value = event.getNewValue().doubleValue();
            budgetService.updateBudget(selectedMonth, row.getCategoryId(), value);
            loadMonth(selectedMonth);
        });
        budgetColumn.setPrefWidth(180);

        table.getColumns().addAll(categoryColumn, actualColumn, budgetColumn, differenceColumn, balanceColumn);
        table.setRowFactory(tv -> createRow(moveAction, allowMove));

        return table;
    }

    private TableColumn<BudgetTableRow, Number> moneyColumn(
        String name,
        java.util.function.ToDoubleFunction<BudgetTableRow> getter,
        NumberFormat format
    ) {
        TableColumn<BudgetTableRow, Number> column = new TableColumn<>(name);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(getter.applyAsDouble(cell.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(format.format(item.doubleValue()));
                }
            }
        });
        column.setPrefWidth(180);
        return column;
    }

    private TableRow<BudgetTableRow> createRow(Consumer<BudgetTableRow> moveAction, boolean allowMove) {
        TableRow<BudgetTableRow> row = new TableRow<>();
        row.itemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                row.getStyleClass().remove("rollup-row");
                row.setContextMenu(null);
                return;
            }

            row.getStyleClass().remove("rollup-row");
            if (newItem.isRollup()) {
                row.getStyleClass().add("rollup-row");
            }

            if (!allowMove || moveAction == null || newItem.getType() == CategoryType.INCOME) {
                row.setContextMenu(null);
                return;
            }

            String label = newItem.getType() == CategoryType.MANDATORY
                ? "Move to Discretionary"
                : "Move to Mandatory";
            MenuItem moveItem = new MenuItem(label);
            moveItem.setOnAction(event -> {
                moveAction.accept(newItem);
                loadMonth(selectedMonth);
            });

            ContextMenu contextMenu = new ContextMenu(moveItem);
            row.setContextMenu(contextMenu);
        });
        return row;
    }

    private void importCsv(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Actuals CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialDirectory(new File("."));

        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        try {
            YearMonth importedMonth = csvActualImporter.importFile(file.toPath());
            selectMonth(importedMonth, true);
            statusLabel.setText("Imported " + file.getName() + " for " + MONTH_DISPLAY.format(importedMonth));
        } catch (Exception ex) {
            showError("Import failed", ex.getMessage());
        }
    }

    private void refreshMonths() {
        List<YearMonth> months = repository.getAvailableMonths();
        if (months.isEmpty()) {
            repository.ensureMonthlyClassificationsFromDefaults(selectedMonth);
            months = repository.getAvailableMonths();
        }
        monthPicker.setItems(FXCollections.observableArrayList(months));
        if (!months.contains(selectedMonth)) {
            selectedMonth = months.isEmpty() ? selectedMonth : months.get(months.size() - 1);
        }
    }

    private void loadMonth(YearMonth month) {
        try {
            MonthSnapshot snapshot = budgetService.loadMonth(month);
            incomeRows.setAll(snapshot.income().stream().map(BudgetTableRow::new).toList());
            mandatoryRows.setAll(snapshot.mandatory().stream().map(BudgetTableRow::new).toList());
            discretionaryRows.setAll(snapshot.discretionary().stream().map(BudgetTableRow::new).toList());
            statusLabel.setText("Showing " + MONTH_DISPLAY.format(month));
        } catch (Exception ex) {
            showError("Load failed", ex.getMessage());
        }
    }

    private void selectMonth(YearMonth month, boolean ensureMonthExists) {
        selectedMonth = month;
        if (ensureMonthExists) {
            repository.ensureMonthlyClassificationsFromDefaults(month);
        }
        refreshMonths();
        monthPicker.setValue(month);
        loadMonth(month);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
