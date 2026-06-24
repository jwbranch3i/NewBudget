package com.newbudget;

import com.newbudget.data.BudgetRepository;
import com.newbudget.data.CsvActualImporter;
import com.newbudget.data.Database;
import com.newbudget.model.CategoryType;
import com.newbudget.model.MonthSnapshot;
import com.newbudget.service.BudgetService;
import com.newbudget.ui.BudgetTableRow;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class BudgetApp extends Application {
    private static final DateTimeFormatter MONTH_DISPLAY = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final double CATEGORY_COL_WIDTH = 290;
    private static final double MONEY_COL_WIDTH = 110;
    private static final String ROLLUP_ROW_CLASS = "rollup-row";
    private static final String TOTALS_CELL_CLASS = "totals-cell";
    private static final String ZERO_MONEY = "$0.00";

    private final BudgetRepository repository = new BudgetRepository();
    private final BudgetService budgetService = new BudgetService(repository);
    private final CsvActualImporter csvActualImporter = new CsvActualImporter(repository);

    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();
    private final Label selectedMonthLabel = new Label();
    private final Label statusLabel = new Label("Ready");

    private TreeTableView<BudgetTableRow> incomeTable;
    private TreeTableView<BudgetTableRow> mandatoryTable;
    private TreeTableView<BudgetTableRow> discretionaryTable;

    private Label incomeActualTotal;
    private Label incomeBudgetTotal;
    private Label incomeDifferenceTotal;
    private Label incomeBalanceTotal;

    private Label mandatoryActualTotal;
    private Label mandatoryBudgetTotal;
    private Label mandatoryDifferenceTotal;
    private Label mandatoryBalanceTotal;

    private Label discretionaryActualTotal;
    private Label discretionaryBudgetTotal;
    private Label discretionaryDifferenceTotal;
    private Label discretionaryBalanceTotal;

    private YearMonth selectedMonth;
    private boolean updatingMonthPicker;

    @Override
    public void start(Stage stage) {
        Database.initialize();
        selectedMonth = repository.getLatestMonthWithData().orElse(YearMonth.now(ZoneId.systemDefault()));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setPadding(new Insets(10));
        root.setTop(buildTopBar(stage));
        root.setCenter(buildTables());

        selectedMonthLabel.getStyleClass().add("selected-month-label");
        statusLabel.getStyleClass().add("status-label");
        VBox bottom = new VBox(6, selectedMonthLabel, statusLabel);
        bottom.setAlignment(Pos.CENTER);
        bottom.getStyleClass().add("bottom-summary");
        root.setBottom(bottom);

        refreshMonths();
        selectMonth(selectedMonth, false);

        Scene scene = new Scene(root, 1600, 900);
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
            if (updatingMonthPicker) {
                return;
            }
            YearMonth chosen = monthPicker.getValue();
            if (chosen != null && !chosen.equals(selectedMonth)) {
                selectMonth(chosen, false);
            }
        });
        monthPicker.getStyleClass().add("month-picker");

        Button importCsvButton = new Button("Import Actuals CSV");
        importCsvButton.getStyleClass().add("action-button");
        importCsvButton.setOnAction(event -> importCsv(stage));

        Button currentMonthButton = new Button("Use Current Month");
        currentMonthButton.getStyleClass().add("secondary-button");
        currentMonthButton.setOnAction(event -> selectMonth(YearMonth.now(ZoneId.systemDefault()), true));

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

    private HBox buildTables() {
        incomeTable = createTable(false, null);
        mandatoryTable = createTable(
            true,
            row -> budgetService.updateMonthlyClassificationGroup(selectedMonth, row.getCategoryId(), CategoryType.DISCRETIONARY)
        );
        discretionaryTable = createTable(
            true,
            row -> budgetService.updateMonthlyClassificationGroup(selectedMonth, row.getCategoryId(), CategoryType.MANDATORY)
        );

        VBox incomePane = createSectionPane("INCOME", incomeTable, (actual, budget, difference, balance) -> {
            incomeActualTotal = actual;
            incomeBudgetTotal = budget;
            incomeDifferenceTotal = difference;
            incomeBalanceTotal = balance;
        });

        VBox mandatoryPane = createSectionPane("MANDATORY", mandatoryTable, (actual, budget, difference, balance) -> {
            mandatoryActualTotal = actual;
            mandatoryBudgetTotal = budget;
            mandatoryDifferenceTotal = difference;
            mandatoryBalanceTotal = balance;
        });

        VBox discretionaryPane = createSectionPane("DISCRETIONARY", discretionaryTable, (actual, budget, difference, balance) -> {
            discretionaryActualTotal = actual;
            discretionaryBudgetTotal = budget;
            discretionaryDifferenceTotal = difference;
            discretionaryBalanceTotal = balance;
        });

        HBox container = new HBox(12, incomePane, mandatoryPane, discretionaryPane);
        container.getStyleClass().add("sections-root");
        HBox.setHgrow(incomePane, Priority.ALWAYS);
        HBox.setHgrow(mandatoryPane, Priority.ALWAYS);
        HBox.setHgrow(discretionaryPane, Priority.ALWAYS);
        return container;
    }

    @FunctionalInterface
    private interface TotalsSetter {
        void set(Label actual, Label budget, Label difference, Label balance);
    }

    private VBox createSectionPane(String title, TreeTableView<BudgetTableRow> table, TotalsSetter totalsSetter) {
        Label sectionTitle = sectionLabel(title);
        GridPane totals = createTotalsRow();

        Label actual = (Label) totals.lookup("#totalActual");
        Label budget = (Label) totals.lookup("#totalBudget");
        Label difference = (Label) totals.lookup("#totalDifference");
        Label balance = (Label) totals.lookup("#totalBalance");
        totalsSetter.set(actual, budget, difference, balance);

        VBox section = new VBox(6, sectionTitle, table, totals);
        section.getStyleClass().add("section-card");
        section.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        return section;
    }

    private GridPane createTotalsRow() {
        GridPane totals = new GridPane();
        totals.getStyleClass().add("totals-row");

        Label totalLabel = new Label("TOTAL");
        totalLabel.getStyleClass().addAll(TOTALS_CELL_CLASS, "totals-title");
        totalLabel.setPrefWidth(CATEGORY_COL_WIDTH);

        Label actualLabel = new Label(ZERO_MONEY);
        actualLabel.setId("totalActual");
        actualLabel.getStyleClass().add(TOTALS_CELL_CLASS);
        actualLabel.setPrefWidth(MONEY_COL_WIDTH);

        Label budgetLabel = new Label(ZERO_MONEY);
        budgetLabel.setId("totalBudget");
        budgetLabel.getStyleClass().add(TOTALS_CELL_CLASS);
        budgetLabel.setPrefWidth(MONEY_COL_WIDTH);

        Label differenceLabel = new Label(ZERO_MONEY);
        differenceLabel.setId("totalDifference");
        differenceLabel.getStyleClass().add(TOTALS_CELL_CLASS);
        differenceLabel.setPrefWidth(MONEY_COL_WIDTH);

        Label balanceLabel = new Label(ZERO_MONEY);
        balanceLabel.setId("totalBalance");
        balanceLabel.getStyleClass().add(TOTALS_CELL_CLASS);
        balanceLabel.setPrefWidth(MONEY_COL_WIDTH);

        totals.add(totalLabel, 0, 0);
        totals.add(actualLabel, 1, 0);
        totals.add(budgetLabel, 2, 0);
        totals.add(differenceLabel, 3, 0);
        totals.add(balanceLabel, 4, 0);
        return totals;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private TreeTableView<BudgetTableRow> createTable(boolean allowMove, Consumer<BudgetTableRow> moveAction) {
        TreeTableView<BudgetTableRow> table = new TreeTableView<>();
        table.setShowRoot(false);
        table.setEditable(true);
        table.getStyleClass().add("budget-table");

        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
        TreeTableColumn<BudgetTableRow, String> categoryColumn = createCategoryColumn();
        TreeTableColumn<BudgetTableRow, Number> actualColumn =
            moneyColumn("Actual", BudgetTableRow::getActualAmount, money);
        TreeTableColumn<BudgetTableRow, Number> budgetColumn = createBudgetColumn(money);
        TreeTableColumn<BudgetTableRow, Number> differenceColumn =
            moneyColumn(
                "Diff",
                row -> row.isRollup() ? row.getBudgetAmount() - row.getActualAmount() : row.getDifference(),
                money
            );
        TreeTableColumn<BudgetTableRow, Number> balanceColumn =
            moneyColumn("Balance", BudgetTableRow::getBalance, money);

        table.getColumns().add(categoryColumn);
        table.getColumns().add(actualColumn);
        table.getColumns().add(budgetColumn);
        table.getColumns().add(differenceColumn);
        table.getColumns().add(balanceColumn);
        table.setRowFactory(tv -> createRow(moveAction, allowMove));
        return table;
    }

    private TreeTableColumn<BudgetTableRow, String> createCategoryColumn() {
        TreeTableColumn<BudgetTableRow, String> categoryColumn = new TreeTableColumn<>("Category");
        categoryColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
            cell.getValue().getValue().getCategory()
        ));
        categoryColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
                setStyle("-fx-alignment: center-left;");
            }
        });
        categoryColumn.setPrefWidth(CATEGORY_COL_WIDTH);
        return categoryColumn;
    }

    private TreeTableColumn<BudgetTableRow, Number> createBudgetColumn(NumberFormat money) {
        TreeTableColumn<BudgetTableRow, Number> budgetColumn = new TreeTableColumn<>("Budget");
        budgetColumn.setCellValueFactory(cell -> cell.getValue().getValue().budgetAmountProperty());
        budgetColumn.setCellFactory(col -> new TextFieldTreeTableCell<>(new StringConverter<>() {
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
        }) {
            @Override
            public void startEdit() {
                TreeTableView<BudgetTableRow> treeTable = getTreeTableView();
                if (treeTable == null) {
                    return;
                }

                TreeItem<BudgetTableRow> treeItem = treeTable.getTreeItem(getIndex());
                if (treeItem == null || treeItem.getValue() == null || treeItem.getValue().isRollup()) {
                    return;
                }

                super.startEdit();
            }
        });
        budgetColumn.setOnEditCommit(event -> {
            TreeItem<BudgetTableRow> treeItem = event.getRowValue();
            if (treeItem == null || treeItem.getValue() == null || treeItem.getValue().isRollup()) {
                return;
            }

            BudgetTableRow row = treeItem.getValue();
            double value = event.getNewValue().doubleValue();
            budgetService.updateBudget(selectedMonth, row.getCategoryId(), value);
            loadMonth(selectedMonth);
        });
        budgetColumn.setPrefWidth(MONEY_COL_WIDTH);
        return budgetColumn;
    }

    private TreeTableColumn<BudgetTableRow, Number> moneyColumn(
        String name,
        java.util.function.ToDoubleFunction<BudgetTableRow> getter,
        NumberFormat format
    ) {
        TreeTableColumn<BudgetTableRow, Number> column = new TreeTableColumn<>(name);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(
            getter.applyAsDouble(cell.getValue().getValue())
        ));
        column.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(format.format(item.doubleValue()));
                }
                setStyle("-fx-alignment: center-right;");
            }
        });
        column.setPrefWidth(MONEY_COL_WIDTH);
        return column;
    }

    private TreeTableRow<BudgetTableRow> createRow(Consumer<BudgetTableRow> moveAction, boolean allowMove) {
        TreeTableRow<BudgetTableRow> row = new TreeTableRow<>();
        row.itemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                row.getStyleClass().remove(ROLLUP_ROW_CLASS);
                row.setContextMenu(null);
                return;
            }

            row.getStyleClass().remove(ROLLUP_ROW_CLASS);
            if (newItem.isRollup()) {
                row.getStyleClass().add(ROLLUP_ROW_CLASS);
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
            row.setContextMenu(new ContextMenu(moveItem));
        });
        return row;
    }

    private TreeItem<BudgetTableRow> buildTree(List<BudgetTableRow> rows) {
        TreeItem<BudgetTableRow> root = new TreeItem<>();
        List<TreeItem<BudgetTableRow>> stack = new ArrayList<>();

        for (BudgetTableRow row : rows) {
            int depth = Math.max(0, row.getDepth());
            TreeItem<BudgetTableRow> item = new TreeItem<>(row);
            item.setExpanded(true);

            while (stack.size() > depth) {
                stack.remove(stack.size() - 1);
            }

            TreeItem<BudgetTableRow> parent = stack.isEmpty() ? root : stack.get(stack.size() - 1);
            parent.getChildren().add(item);
            stack.add(item);
        }

        return root;
    }

    private void applySection(TreeTableView<BudgetTableRow> table, List<BudgetTableRow> rows) {
        TreeItem<BudgetTableRow> root = buildTree(rows);
        enforceRollupBudgetFromLeaves(root);
        table.setRoot(root);
        table.refresh();
    }

    private double enforceRollupBudgetFromLeaves(TreeItem<BudgetTableRow> item) {
        if (item == null) {
            return 0.0;
        }

        BudgetTableRow row = item.getValue();
        if (item.getChildren().isEmpty()) {
            return row == null ? 0.0 : row.getBudgetAmount();
        }

        double leafBudgetSum = 0.0;
        for (TreeItem<BudgetTableRow> child : item.getChildren()) {
            leafBudgetSum += enforceRollupBudgetFromLeaves(child);
        }

        if (row != null && row.isRollup()) {
            row.setBudgetAmount(leafBudgetSum);
        }

        return leafBudgetSum;
    }

    private void setTotals(
        Label actual,
        Label budget,
        Label difference,
        Label balance,
        List<BudgetTableRow> rows
    ) {
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
        double actualTotal = rows.stream().filter(row -> row.getDepth() == 0).mapToDouble(BudgetTableRow::getActualAmount).sum();
        double budgetTotal = rows.stream().filter(row -> row.getDepth() == 0).mapToDouble(BudgetTableRow::getBudgetAmount).sum();
        double differenceTotal = rows.stream().filter(row -> row.getDepth() == 0).mapToDouble(BudgetTableRow::getDifference).sum();
        double balanceTotal = rows.stream().filter(row -> row.getDepth() == 0).mapToDouble(BudgetTableRow::getBalance).sum();

        actual.setText(money.format(actualTotal));
        budget.setText(money.format(budgetTotal));
        difference.setText(money.format(differenceTotal));
        balance.setText(money.format(balanceTotal));
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
        YearMonth monthToShow = selectedMonth;
        if (!months.contains(monthToShow)) {
            monthToShow = months.isEmpty() ? monthToShow : months.get(months.size() - 1);
            selectedMonth = monthToShow;
        }

        updatingMonthPicker = true;
        try {
            monthPicker.setItems(FXCollections.observableArrayList(months));
            monthPicker.setValue(monthToShow);
        } finally {
            updatingMonthPicker = false;
        }
    }

    private void loadMonth(YearMonth month) {
        try {
            MonthSnapshot snapshot = budgetService.loadMonth(month);
            List<BudgetTableRow> income = snapshot.income().stream().map(BudgetTableRow::new).toList();
            List<BudgetTableRow> mandatory = snapshot.mandatory().stream().map(BudgetTableRow::new).toList();
            List<BudgetTableRow> discretionary = snapshot.discretionary().stream().map(BudgetTableRow::new).toList();

            applySection(incomeTable, income);
            applySection(mandatoryTable, mandatory);
            applySection(discretionaryTable, discretionary);

            setTotals(incomeActualTotal, incomeBudgetTotal, incomeDifferenceTotal, incomeBalanceTotal, income);
            setTotals(mandatoryActualTotal, mandatoryBudgetTotal, mandatoryDifferenceTotal, mandatoryBalanceTotal, mandatory);
            setTotals(
                discretionaryActualTotal,
                discretionaryBudgetTotal,
                discretionaryDifferenceTotal,
                discretionaryBalanceTotal,
                discretionary
            );

            selectedMonthLabel.setText(MONTH_DISPLAY.format(month));
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
