package com.newbudget.ui;

import com.newbudget.data.BudgetRepository;
import com.newbudget.data.CsvActualImporter;
import com.newbudget.model.CategoryType;
import com.newbudget.model.MonthSnapshot;
import com.newbudget.service.BudgetService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

public class BudgetViewController {
    private static final DateTimeFormatter MONTH_DISPLAY = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final double CATEGORY_COL_WIDTH = 200;  //290;
    private static final double MONEY_COL_WIDTH = 75;  //110;
    private static final String ROLLUP_ROW_CLASS = "rollup-row";
    private static final String ZERO_MONEY = "$0.00";

    private BudgetRepository repository;
    private BudgetService budgetService;
    private CsvActualImporter csvActualImporter;
    private Stage stage;
    private YearMonth selectedMonth;
    private boolean updatingMonthPicker;

    @FXML
    private ComboBox<YearMonth> monthPicker;
    @FXML
    private Label selectedMonthLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private TreeTableView<BudgetTableRow> incomeTable;
    @FXML
    private TreeTableView<BudgetTableRow> mandatoryTable;
    @FXML
    private TreeTableView<BudgetTableRow> discretionaryTable;

    @FXML
    private TreeTableColumn<BudgetTableRow, String> incomeCategoryColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> incomeActualColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> incomeBudgetColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> incomeDifferenceColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> incomeBalanceColumn;

    @FXML
    private TreeTableColumn<BudgetTableRow, String> mandatoryCategoryColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> mandatoryActualColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> mandatoryBudgetColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> mandatoryDifferenceColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> mandatoryBalanceColumn;

    @FXML
    private TreeTableColumn<BudgetTableRow, String> discretionaryCategoryColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> discretionaryActualColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> discretionaryBudgetColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> discretionaryDifferenceColumn;
    @FXML
    private TreeTableColumn<BudgetTableRow, Number> discretionaryBalanceColumn;

    @FXML
    private Label incomeActualTotal;
    @FXML
    private Label incomeBudgetTotal;
    @FXML
    private Label incomeDifferenceTotal;
    @FXML
    private Label incomeBalanceTotal;

    @FXML
    private Label mandatoryActualTotal;
    @FXML
    private Label mandatoryBudgetTotal;
    @FXML
    private Label mandatoryDifferenceTotal;
    @FXML
    private Label mandatoryBalanceTotal;

    @FXML
    private Label discretionaryActualTotal;
    @FXML
    private Label discretionaryBudgetTotal;
    @FXML
    private Label discretionaryDifferenceTotal;
    @FXML
    private Label discretionaryBalanceTotal;

    @FXML
    private void initialize() {
        statusLabel.setText("Ready");
        selectedMonthLabel.setText("");
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
        monthPicker.setOnAction(event -> onMonthPicked());

        configureTable(incomeTable, incomeCategoryColumn, incomeActualColumn, incomeBudgetColumn, incomeDifferenceColumn, incomeBalanceColumn, false, null);
        configureTable(
            mandatoryTable,
            mandatoryCategoryColumn,
            mandatoryActualColumn,
            mandatoryBudgetColumn,
            mandatoryDifferenceColumn,
            mandatoryBalanceColumn,
            true,
            row -> budgetService.updateMonthlyClassificationGroup(selectedMonth, row.getCategoryId(), CategoryType.DISCRETIONARY)
        );
        configureTable(
            discretionaryTable,
            discretionaryCategoryColumn,
            discretionaryActualColumn,
            discretionaryBudgetColumn,
            discretionaryDifferenceColumn,
            discretionaryBalanceColumn,
            true,
            row -> budgetService.updateMonthlyClassificationGroup(selectedMonth, row.getCategoryId(), CategoryType.MANDATORY)
        );

        initializeTotals();
    }

    public void initializeApp(
        Stage stage,
        BudgetRepository repository,
        BudgetService budgetService,
        CsvActualImporter csvActualImporter
    ) {
        this.stage = stage;
        this.repository = repository;
        this.budgetService = budgetService;
        this.csvActualImporter = csvActualImporter;
        YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
        this.selectedMonth = determineInitialMonth(currentMonth, repository.getAvailableMonths());
        refreshMonths();
        selectMonth(selectedMonth, false);
    }

    static YearMonth determineInitialMonth(YearMonth currentMonth, List<YearMonth> availableMonths) {
        if (availableMonths.contains(currentMonth)) {
            return currentMonth;
        }

        if (!availableMonths.isEmpty()) {
            return availableMonths.get(availableMonths.size() - 1);
        }

        return currentMonth;
    }

    @FXML
    private void onPreviousMonth() {
        if (selectedMonth == null) {
            showInfo("No Month Selected", "There is no selected month.");
            return;
        }

        List<YearMonth> months = repository.getAvailableMonths();
        YearMonth previous = null;
        for (YearMonth m : months) {
            if (m.isBefore(selectedMonth)) {
                previous = m;
            }
        }
        if (previous != null) {
            selectMonth(previous, false);
        } else {
            showInfo("No Earlier Month", "There is no earlier month with data.");
        }
    }

    @FXML
    private void onNextMonth() {
        if (selectedMonth == null) {
            showInfo("No Month Selected", "There is no selected month.");
            return;
        }

        List<YearMonth> months = repository.getAvailableMonths();
        YearMonth next = null;
        for (YearMonth m : months) {
            if (m.isAfter(selectedMonth)) {
                next = m;
                break;
            }
        }
        if (next != null) {
            selectMonth(next, false);
        } else {
            showInfo("No Later Month", "There is no later month with data.");
        }
    }

    @FXML
    private void onUseCurrentMonth() {
        selectMonth(YearMonth.now(ZoneId.systemDefault()), true);
    }

    @FXML
    private void onImportCsv() {
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

    @FXML
    private void onDeleteCurrentMonthData() {
        if (selectedMonth == null) {
            showInfo("No Month Selected", "There is no selected month to delete.");
            return;
        }

        String monthName = MONTH_DISPLAY.format(selectedMonth);
        boolean confirmed = confirmAction(
            "Delete Current Month Data",
            "Delete all data for " + monthName + "?",
            "This removes budgets, actuals, and month-specific classifications for this month.",
            "Delete Month"
        );
        if (!confirmed) {
            statusLabel.setText("Delete canceled");
            return;
        }

        try {
            budgetService.deleteMonthData(selectedMonth);
            selectMonth(selectedMonth, false);
            statusLabel.setText("Deleted all data for " + monthName);
        } catch (Exception ex) {
            showError("Delete failed", ex.getMessage());
        }
    }

    @FXML
    private void onDeleteAllMonthsData() {
        boolean firstConfirmation = confirmAction(
            "Delete All Months Data",
            "Delete all data for every month?",
            "This cannot be undone and removes budgets, actuals, and monthly classifications from all months.",
            "Continue"
        );
        if (!firstConfirmation) {
            statusLabel.setText("Delete canceled");
            return;
        }

        boolean secondConfirmation = confirmAction(
            "Final Confirmation",
            "Are you absolutely sure?",
            "Choose Delete All only if you really intend to clear every month.",
            "Delete All"
        );
        if (!secondConfirmation) {
            statusLabel.setText("Delete canceled");
            return;
        }

        try {
            budgetService.deleteAllMonthsData();
            selectMonth(YearMonth.now(ZoneId.systemDefault()), false);
            statusLabel.setText("Deleted all month data");
        } catch (Exception ex) {
            showError("Delete failed", ex.getMessage());
        }
    }

    private void onMonthPicked() {
        if (updatingMonthPicker) {
            return;
        }

        YearMonth chosen = monthPicker.getValue();
        if (chosen != null && !chosen.equals(selectedMonth)) {
            selectMonth(chosen, false);
        }
    }

    private void configureTable(
        TreeTableView<BudgetTableRow> table,
        TreeTableColumn<BudgetTableRow, String> categoryColumn,
        TreeTableColumn<BudgetTableRow, Number> actualColumn,
        TreeTableColumn<BudgetTableRow, Number> budgetColumn,
        TreeTableColumn<BudgetTableRow, Number> differenceColumn,
        TreeTableColumn<BudgetTableRow, Number> balanceColumn,
        boolean allowMove,
        Consumer<BudgetTableRow> moveAction
    ) {
        table.setShowRoot(false);
        table.setEditable(true);
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);

        configureCategoryColumn(categoryColumn);
        configureMoneyColumn(actualColumn, BudgetTableRow::getActualAmount, money);
        configureBudgetColumn(budgetColumn, money);
        configureMoneyColumn(
            differenceColumn,
            row -> row.isRollup() ? row.getBudgetAmount() - row.getActualAmount() : row.getDifference(),
            money
        );
        configureBalanceColumn(balanceColumn, money);
        table.setRowFactory(tv -> createRow(moveAction, allowMove));
    }

    private void configureCategoryColumn(TreeTableColumn<BudgetTableRow, String> column) {
        column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValue().getCategory()));
        column.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setStyle("-fx-alignment: center-left;");
            }
        });
        column.setPrefWidth(CATEGORY_COL_WIDTH);
    }

    private void configureBudgetColumn(TreeTableColumn<BudgetTableRow, Number> column, NumberFormat money) {
        column.setCellValueFactory(cell -> cell.getValue().getValue().budgetAmountProperty());
        column.setCellFactory(col -> new TextFieldTreeTableCell<>(new StringConverter<>() {
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
        column.setOnEditCommit(event -> onBudgetEdited(event.getRowValue(), event.getNewValue()));
        column.setPrefWidth(MONEY_COL_WIDTH);
    }

    private void onBudgetEdited(TreeItem<BudgetTableRow> treeItem, Number newValue) {
        if (treeItem == null || treeItem.getValue() == null || treeItem.getValue().isRollup()) {
            return;
        }

        BudgetTableRow row = treeItem.getValue();
        budgetService.updateBudget(selectedMonth, row.getCategoryId(), newValue.doubleValue());
        loadMonth(selectedMonth);
    }

    private void configureBalanceColumn(TreeTableColumn<BudgetTableRow, Number> column, NumberFormat money) {
        column.setCellValueFactory(cell -> cell.getValue().getValue().balanceProperty());
        column.setCellFactory(col -> new TextFieldTreeTableCell<>(new StringConverter<>() {
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
                if (treeItem == null || treeItem.getValue() == null || !treeItem.getChildren().isEmpty()) {
                    return;
                }

                super.startEdit();
            }
        });
        column.setOnEditCommit(event -> onBalanceEdited(event.getRowValue(), event.getNewValue()));
        column.setPrefWidth(MONEY_COL_WIDTH);
    }

    private void onBalanceEdited(TreeItem<BudgetTableRow> treeItem, Number newValue) {
        if (treeItem == null || treeItem.getValue() == null || !treeItem.getChildren().isEmpty()) {
            return;
        }

        BudgetTableRow row = treeItem.getValue();
        budgetService.updateBalance(selectedMonth, row.getCategoryId(), newValue.doubleValue());
        loadMonth(selectedMonth);
    }

    private void configureMoneyColumn(
        TreeTableColumn<BudgetTableRow, Number> column,
        ToDoubleFunction<BudgetTableRow> getter,
        NumberFormat format
    ) {
        column.setCellValueFactory(cell -> new SimpleDoubleProperty(getter.applyAsDouble(cell.getValue().getValue())));
        column.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.format(item.doubleValue()));
                setStyle("-fx-alignment: center-right;");
            }
        });
        column.setPrefWidth(MONEY_COL_WIDTH);
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

    private void initializeTotals() {
        for (Label label : List.of(
            incomeActualTotal,
            incomeBudgetTotal,
            incomeDifferenceTotal,
            incomeBalanceTotal,
            mandatoryActualTotal,
            mandatoryBudgetTotal,
            mandatoryDifferenceTotal,
            mandatoryBalanceTotal,
            discretionaryActualTotal,
            discretionaryBudgetTotal,
            discretionaryDifferenceTotal,
            discretionaryBalanceTotal
        )) {
            label.setText(ZERO_MONEY);
        }
    }

    private void setTotals(Label actual, Label budget, Label difference, Label balance, List<BudgetTableRow> rows) {
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

    private void refreshMonths() {
        List<YearMonth> months = repository.getAvailableMonths();
        if (months.isEmpty()) {
            selectedMonth = null;
            updatingMonthPicker = true;
            try {
                monthPicker.setItems(FXCollections.observableArrayList());
                monthPicker.setValue(null);
            } finally {
                updatingMonthPicker = false;
            }
            return;
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
        if (selectedMonth == null) {
            clearVisibleData();
            selectedMonthLabel.setText(month == null ? "" : MONTH_DISPLAY.format(month));
            statusLabel.setText("No month data available");
            return;
        }
        loadMonth(selectedMonth);
    }

    private void clearVisibleData() {
        incomeTable.setRoot(new TreeItem<>());
        mandatoryTable.setRoot(new TreeItem<>());
        discretionaryTable.setRoot(new TreeItem<>());
        incomeTable.refresh();
        mandatoryTable.refresh();
        discretionaryTable.refresh();
        initializeTotals();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean confirmAction(String title, String header, String message, String confirmText) {
        ButtonType confirmButton = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(confirmButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == confirmButton;
    }
}