package com.newbudget.ui;

import com.newbudget.data.BudgetRepository;
import com.newbudget.data.CsvActualImporter;
import com.newbudget.model.AccountRecord;
import com.newbudget.model.CategoryType;
import com.newbudget.model.MonthSnapshot;
import com.newbudget.service.BudgetService;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

public class BudgetViewController {
    private static final DateTimeFormatter MONTH_DISPLAY = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final double CATEGORY_COL_WIDTH = 200;  //290;
    private static final double MONEY_COL_WIDTH = 75;  //110;
    private static final String ROLLUP_ROW_CLASS = "rollup-row";
    private static final String MASTER_ROW_CLASS = "master-row";
    private static final String HIDDEN_ROW_CLASS = "hidden-row";
    private static final String ZERO_MONEY = "$0.00";

    private BudgetRepository repository;
    private BudgetService budgetService;
    private CsvActualImporter csvActualImporter;
    private Stage stage;
    private YearMonth selectedMonth;
    private boolean updatingMonthPicker;
    private Integer accountId;
    private String accountName;

    @FXML
    private ComboBox<YearMonth> monthPicker;
    @FXML
    private Label selectedMonthLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private CheckBox showHiddenToggle;

    @FXML
    private Button createAccountButton;

    @FXML
    private Button openAccountButton;

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
    private Label summaryIncomeActual;
    @FXML
    private Label summaryIncomeBudget;
    @FXML
    private Label summaryIncomeDiff;
    @FXML
    private Label summaryIncomeBalance;

    @FXML
    private Label summaryMandatoryActual;
    @FXML
    private Label summaryMandatoryBudget;
    @FXML
    private Label summaryMandatoryDiff;
    @FXML
    private Label summaryMandatoryBalance;

    @FXML
    private Label summaryDiscretionaryActual;
    @FXML
    private Label summaryDiscretionaryBudget;
    @FXML
    private Label summaryDiscretionaryDiff;
    @FXML
    private Label summaryDiscretionaryBalance;

    @FXML
    private Label summaryNetActual;
    @FXML
    private Label summaryNetBudget;
    @FXML
    private Label summaryNetDiff;
    @FXML
    private Label summaryNetBalance;

    private final Map<TreeItem<BudgetTableRow>, ListChangeListener<TreeItem<BudgetTableRow>>> treeChildrenListeners =
        new IdentityHashMap<>();
    private final Map<BudgetTableRow, ChangeListener<Number>> rowBudgetListeners = new IdentityHashMap<>();
    private final Map<BudgetTableRow, ChangeListener<Number>> rowBalanceListeners = new IdentityHashMap<>();

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
        showHiddenToggle.setOnAction(event -> onShowHiddenToggled());

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

        installReactiveSummaryListeners();
        initializeTotals();
    }

    public void initializeApp(
        Stage stage,
        BudgetRepository repository,
        BudgetService budgetService,
        CsvActualImporter csvActualImporter
    ) {
        initializeCommon(stage, repository, budgetService, csvActualImporter, null, null);
    }

    public void initializeAccountWindow(
        Stage stage,
        BudgetRepository repository,
        BudgetService budgetService,
        CsvActualImporter csvActualImporter,
        AccountRecord account
    ) {
        initializeCommon(stage, repository, budgetService, csvActualImporter, account.id(), account.name());
    }

    private void initializeCommon(
        Stage stage,
        BudgetRepository repository,
        BudgetService budgetService,
        CsvActualImporter csvActualImporter,
        Integer accountId,
        String accountName
    ) {
        this.stage = stage;
        this.repository = repository;
        this.budgetService = budgetService;
        this.csvActualImporter = csvActualImporter;
        this.accountId = accountId;
        this.accountName = accountName;
        YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
        this.selectedMonth = determineInitialMonth(currentMonth, repository.getAvailableMonths());
        if (this.stage != null && this.accountName != null) {
            this.stage.setTitle("NewBudget - " + this.accountName);
        }
        refreshMonths();
        updateWindowModeControls();
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
    private void onAddCategory() {
        if (selectedMonth == null) {
            showInfo("No Month Selected", "Select a month before adding categories.");
            return;
        }

        Optional<NewCategoryInput> input = promptForNewCategoryInput();
        if (input.isEmpty()) {
            return;
        }

        NewCategoryInput value = input.get();
        Integer parentId = value.isParent() ? null : value.parent().id();
        CategoryType sectionType = value.isParent() ? value.parentType() : null;

        try {
            budgetService.addCategory(selectedMonth, value.name(), parentId, sectionType);
            loadMonth(selectedMonth);
            String target = value.isParent()
                ? "as " + value.parentType().name().charAt(0) + value.parentType().name().substring(1).toLowerCase(Locale.ROOT)
                : "under \"" + value.parent().label() + "\"";
            statusLabel.setText("Added category \"" + value.name() + "\" " + target + " for " + MONTH_DISPLAY.format(selectedMonth));
        } catch (IllegalArgumentException ex) {
            showError("Add Category Failed", ex.getMessage());
        } catch (Exception ex) {
            showError("Add Category Failed", "Unable to add category.");
        }
    }

    @FXML
    private void onCreateAccount() {
        Optional<String> name = promptForAccountName();
        if (name.isEmpty()) {
            return;
        }

        try {
            int createdAccountId = budgetService.createAccount(name.get());
            statusLabel.setText("Created account \"" + name.get() + "\".");
            budgetService.getAccounts().stream()
                .filter(account -> account.id() == createdAccountId)
                .findFirst()
                .ifPresent(this::openAccountWindow);
        } catch (IllegalArgumentException ex) {
            showError("Create Account Failed", ex.getMessage());
        } catch (Exception ex) {
            showError("Create Account Failed", "Unable to create account.");
        }
    }

    @FXML
    private void onOpenAccount() {
        List<AccountRecord> accounts = budgetService.getAccounts();
        if (accounts.isEmpty()) {
            showInfo("No Accounts", "Create an account before opening one.");
            return;
        }

        Optional<AccountRecord> selectedAccount = accounts.size() == 1
            ? Optional.of(accounts.get(0))
            : promptForAccountSelection(accounts, "Open Account", "Select an account to open");
        selectedAccount.ifPresent(this::openAccountWindow);
    }

    private Optional<NewCategoryInput> promptForNewCategoryInput() {
        List<com.newbudget.model.CategoryRecord> allCategories = repository.getAllCategories();
        java.util.Set<Integer> parentCategoryIds = allCategories.stream()
            .map(com.newbudget.model.CategoryRecord::parentId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

        List<ParentOption> availableParents = allCategories.stream()
            .filter(category -> category.parentId() == null || parentCategoryIds.contains(category.id()))
            .filter(category -> category.defaultType() != CategoryType.INCOME)
            .sorted((left, right) -> {
                int leftDepth = left.path().split("/").length;
                int rightDepth = right.path().split("/").length;
                if (leftDepth != rightDepth) {
                    return Integer.compare(leftDepth, rightDepth);
                }
                return left.path().compareToIgnoreCase(right.path());
            })
            .map(category -> {
                String[] segments = category.path().split("/");
                int depth = Math.max(0, segments.length - 1);
                String indent = "  ".repeat(depth);
                return new ParentOption(category.id(), indent + category.name());
            })
            .collect(Collectors.toList());

        Dialog<NewCategoryInput> dialog = new Dialog<>();
        dialog.setTitle("Add Category");
        dialog.setHeaderText("Enter category details");

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Category name");

        CheckBox parentCheckBox = new CheckBox("Category is a parent");
        parentCheckBox.setSelected(true);

        ComboBox<CategoryType> parentTypeBox = new ComboBox<>();
        parentTypeBox.setItems(FXCollections.observableArrayList(CategoryType.INCOME, CategoryType.MANDATORY, CategoryType.DISCRETIONARY));
        parentTypeBox.setValue(CategoryType.MANDATORY);

        ComboBox<ParentOption> parentBox = new ComboBox<>(FXCollections.observableArrayList(availableParents));
        parentBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ParentOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        parentBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ParentOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        if (!availableParents.isEmpty()) {
            parentBox.getSelectionModel().selectFirst();
        }

        Label parentTypeLabel = new Label("Parent section:");
        Label parentListLabel = new Label("Place under parent:");

        Runnable refreshControlState = () -> {
            boolean isParent = parentCheckBox.isSelected();
            parentTypeLabel.setDisable(!isParent);
            parentTypeBox.setDisable(!isParent);

            parentListLabel.setDisable(isParent);
            parentBox.setDisable(isParent);
        };
        parentCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> refreshControlState.run());
        refreshControlState.run();

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(parentCheckBox, 0, 0, 2, 1);
        grid.add(parentTypeLabel, 0, 1);
        grid.add(parentTypeBox, 1, 1);
        grid.add(parentListLabel, 0, 2);
        grid.add(parentBox, 1, 2);
        grid.addRow(3, new Label("Category name:"), nameField);

        pane.setContent(grid);

        javafx.scene.Node okButton = pane.lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isBlank()) {
                showError("Add Category Failed", "Category name cannot be blank.");
                event.consume();
                return;
            }

            if (parentCheckBox.isSelected() && parentTypeBox.getValue() == null) {
                showError("Add Category Failed", "Select a section for the parent category.");
                event.consume();
                return;
            }

            if (!parentCheckBox.isSelected() && parentBox.getSelectionModel().getSelectedItem() == null) {
                showError("Add Category Failed", "Select a parent category.");
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }

            String name = nameField.getText().trim();
            boolean isParent = parentCheckBox.isSelected();
            CategoryType parentType = isParent ? parentTypeBox.getValue() : null;
            ParentOption parent = isParent ? null : parentBox.getSelectionModel().getSelectedItem();
            return new NewCategoryInput(name, isParent, parentType, parent);
        });

        return dialog.showAndWait();
    }

    private record ParentOption(int id, String label) {
    }

    private record NewCategoryInput(String name, boolean isParent, CategoryType parentType, ParentOption parent) {
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
        configureMoneyColumn(actualColumn, BudgetTableRow::getActualAmount, money, false);
        configureBudgetColumn(budgetColumn, money);
        configureMoneyColumn(
            differenceColumn,
            row -> row.isRollup() ? row.getBudgetAmount() - row.getActualAmount() : row.getDifference(),
            money,
            true
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
            public void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                BudgetTableRow row = getCurrentRow(getTreeTableRow());
                if (!isEditing() && shouldBlankBudgetLikeCell(row)) {
                    setText("");
                }
                setStyle("-fx-alignment: center-right;");
            }

            @Override
            public void startEdit() {
                TreeTableView<BudgetTableRow> treeTable = getTreeTableView();
                if (treeTable == null) {
                    return;
                }

                TreeItem<BudgetTableRow> treeItem = treeTable.getTreeItem(getIndex());
                if (!canEditBudget(treeItem)) {
                    return;
                }

                super.startEdit();
            }
        });
        column.setOnEditCommit(event -> onBudgetEdited(event.getRowValue(), event.getNewValue()));
        column.setPrefWidth(MONEY_COL_WIDTH);
    }

    private void onBudgetEdited(TreeItem<BudgetTableRow> treeItem, Number newValue) {
        if (!canEditBudget(treeItem)) {
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
            public void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                BudgetTableRow row = getCurrentRow(getTreeTableRow());
                if (!isEditing() && shouldBlankBudgetLikeCell(row)) {
                    setText("");
                }
                setStyle("-fx-alignment: center-right;");
            }

            @Override
            public void startEdit() {
                TreeTableView<BudgetTableRow> treeTable = getTreeTableView();
                if (treeTable == null) {
                    return;
                }

                TreeItem<BudgetTableRow> treeItem = treeTable.getTreeItem(getIndex());
                if (!canEditBalance(treeItem)) {
                    return;
                }

                super.startEdit();
            }
        });
        column.setOnEditCommit(event -> onBalanceEdited(event.getRowValue(), event.getNewValue()));
        column.setPrefWidth(MONEY_COL_WIDTH);
    }

    private void onBalanceEdited(TreeItem<BudgetTableRow> treeItem, Number newValue) {
        if (!canEditBalance(treeItem)) {
            return;
        }

        BudgetTableRow row = treeItem.getValue();
        budgetService.updateBalance(selectedMonth, row.getCategoryId(), newValue.doubleValue());
        loadMonth(selectedMonth);
    }

    private void configureMoneyColumn(
        TreeTableColumn<BudgetTableRow, Number> column,
        ToDoubleFunction<BudgetTableRow> getter,
        NumberFormat format,
        boolean blankWhenChildOfMaster
    ) {
        column.setCellValueFactory(cell -> new SimpleDoubleProperty(getter.applyAsDouble(cell.getValue().getValue())));
        column.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                BudgetTableRow row = getCurrentRow(getTreeTableRow());
                if (empty || item == null) {
                    setText(null);
                } else if (blankWhenChildOfMaster && shouldBlankBudgetLikeCell(row)) {
                    setText("");
                } else {
                    setText(format.format(item.doubleValue()));
                }
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
                row.getStyleClass().remove(MASTER_ROW_CLASS);
                row.getStyleClass().remove(HIDDEN_ROW_CLASS);
                row.setContextMenu(null);
                return;
            }

            row.getStyleClass().remove(ROLLUP_ROW_CLASS);
            row.getStyleClass().remove(MASTER_ROW_CLASS);
            row.getStyleClass().remove(HIDDEN_ROW_CLASS);
            if (newItem.isRollup()) {
                row.getStyleClass().add(ROLLUP_ROW_CLASS);
            }
            if (newItem.isMaster()) {
                row.getStyleClass().add(MASTER_ROW_CLASS);
            }
            if (newItem.isHidden()) {
                row.getStyleClass().add(HIDDEN_ROW_CLASS);
            }

            List<MenuItem> menuItems = new ArrayList<>();

            TreeItem<BudgetTableRow> treeItem = row.getTreeItem();
            if (shouldOfferMasterToggle(newItem, treeItem)) {
                MenuItem masterItem = new MenuItem(newItem.isMaster() ? "Make Regular Category" : "Make Master Category");
                masterItem.setOnAction(event -> {
                    budgetService.updateMasterCategory(newItem.getCategoryId(), !newItem.isMaster());
                    loadMonth(selectedMonth);
                });
                menuItems.add(masterItem);
            }

            String hideLabel = newItem.isHidden() ? "Unhide Category" : "Hide Category";
            MenuItem hideItem = new MenuItem(hideLabel);
            hideItem.setOnAction(event -> {
                budgetService.updateCategoryHiddenState(selectedMonth, newItem.getCategoryId(), !newItem.isHidden());
                loadMonth(selectedMonth);
            });
            menuItems.add(hideItem);

            if (allowMove && moveAction != null && newItem.getType() != CategoryType.INCOME) {
                String moveLabel = newItem.getType() == CategoryType.MANDATORY
                    ? "Move to Discretionary"
                    : "Move to Mandatory";

                MenuItem moveItem = new MenuItem(moveLabel);
                moveItem.setOnAction(event -> {
                    moveAction.accept(newItem);
                    loadMonth(selectedMonth);
                });
                menuItems.add(moveItem);
            }

            if (treeItem != null) {
                if (canAssignCategoryToAccount(newItem)) {
                    MenuItem addToAccountItem = new MenuItem("Add to Account");
                    addToAccountItem.setOnAction(event -> onAssignCategoryToAccount(newItem));
                    menuItems.add(addToAccountItem);
                }

                MenuItem deleteCategoryItem = new MenuItem("Delete Category");
                deleteCategoryItem.setDisable(!isDeleteCategoryEnabled(treeItem));
                deleteCategoryItem.setOnAction(event -> onDeleteCategory(newItem));
                menuItems.add(deleteCategoryItem);
            }

            row.setContextMenu(menuItems.isEmpty() ? null : new ContextMenu(menuItems.toArray(MenuItem[]::new)));
        });
        return row;
    }

    private void onAddChildCategory(BudgetTableRow parentRow) {
        if (selectedMonth == null) {
            showInfo("No Month Selected", "Select a month before adding categories.");
            return;
        }

        Optional<String> name = promptForCategoryName(
            "Add Child Category",
            "Add child under " + parentRow.getCategory(),
            "Enter child category name:"
        );
        if (name.isEmpty()) {
            return;
        }

        try {
            budgetService.addCategory(selectedMonth, name.get(), parentRow.getCategoryId(), null);
            loadMonth(selectedMonth);
            statusLabel.setText("Added child category \"" + name.get() + "\" for " + MONTH_DISPLAY.format(selectedMonth));
        } catch (IllegalArgumentException ex) {
            showError("Add Category Failed", ex.getMessage());
        } catch (Exception ex) {
            showError("Add Category Failed", "Unable to add category.");
        }
    }

    private void onAssignCategoryToAccount(BudgetTableRow row) {
        List<AccountRecord> accounts = budgetService.getAccounts();
        if (accounts.isEmpty()) {
            showInfo("No Accounts", "Create an account before assigning categories.");
            return;
        }

        Optional<AccountRecord> targetAccount = accounts.size() == 1
            ? Optional.of(accounts.get(0))
            : promptForAccountSelection(accounts, "Assign Account", "Assign \"" + row.getCategory() + "\" to an account");
        if (targetAccount.isEmpty()) {
            return;
        }

        try {
            budgetService.assignCategoryToAccount(targetAccount.get().id(), row.getCategoryId());
            statusLabel.setText("Assigned \"" + row.getCategory() + "\" to account \"" + targetAccount.get().name() + "\".");
        } catch (IllegalArgumentException ex) {
            showError("Assign Account Failed", ex.getMessage());
        } catch (Exception ex) {
            showError("Assign Account Failed", "Unable to assign category to account.");
        }
    }

    private void onAddParentCategory() {
        if (selectedMonth == null) {
            showInfo("No Month Selected", "Select a month before adding categories.");
            return;
        }

        Optional<CategoryType> sectionType = promptForParentSection();
        if (sectionType.isEmpty()) {
            return;
        }

        Optional<String> name = promptForCategoryName(
            "Add Parent Category",
            "Create a new top-level category",
            "Enter parent category name:"
        );
        if (name.isEmpty()) {
            return;
        }

        try {
            budgetService.addCategory(selectedMonth, name.get(), null, sectionType.get());
            loadMonth(selectedMonth);
            statusLabel.setText("Added parent category \"" + name.get() + "\" for " + MONTH_DISPLAY.format(selectedMonth));
        } catch (IllegalArgumentException ex) {
            showError("Add Category Failed", ex.getMessage());
        } catch (Exception ex) {
            showError("Add Category Failed", "Unable to add category.");
        }
    }

    private void onDeleteCategory(BudgetTableRow row) {
        if (selectedMonth == null) {
            showInfo("No Month Selected", "Select a month before deleting categories.");
            return;
        }

        boolean confirmed = confirmAction(
            "Delete Category",
            "Delete \"" + row.getCategory() + "\"?",
            "This permanently deletes the category across all months.",
            "Delete"
        );
        if (!confirmed) {
            return;
        }

        try {
            budgetService.deleteCategory(row.getCategoryId());
            loadMonth(selectedMonth);
            statusLabel.setText("Deleted category \"" + row.getCategory() + "\" for " + MONTH_DISPLAY.format(selectedMonth));
        } catch (IllegalArgumentException ex) {
            showError("Delete Category Failed", ex.getMessage());
        } catch (Exception ex) {
            showError("Delete Category Failed", "Unable to delete category.");
        }
    }

    private Optional<String> promptForCategoryName(String title, String header, String content) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        Optional<String> value = dialog.showAndWait().map(String::trim);
        if (value.isPresent() && value.get().isBlank()) {
            showError("Add Category Failed", "Category name cannot be blank.");
            return Optional.empty();
        }
        return value.filter(v -> !v.isBlank());
    }

    private Optional<String> promptForAccountName() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Account");
        dialog.setHeaderText("Create a new account");
        dialog.setContentText("Account name:");

        Optional<String> value = dialog.showAndWait().map(String::trim);
        if (value.isPresent() && value.get().isBlank()) {
            showError("Create Account Failed", "Account name cannot be blank.");
            return Optional.empty();
        }
        return value.filter(v -> !v.isBlank());
    }

    private Optional<AccountRecord> promptForAccountSelection(List<AccountRecord> accounts, String title, String header) {
        ChoiceDialog<AccountRecord> dialog = new ChoiceDialog<>(accounts.get(0), accounts);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText("Account:");
        return dialog.showAndWait();
    }

    private void openAccountWindow(AccountRecord account) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/newbudget/ui/budget-view.fxml"));
            Parent root = loader.load();
            BudgetViewController controller = loader.getController();

            Stage accountStage = new Stage();
            controller.initializeAccountWindow(accountStage, repository, budgetService, csvActualImporter, account);

            Scene scene = new Scene(root, 1600, 900);
            scene.getStylesheets().add(getClass().getResource("/com/newbudget/ui/styles.css").toExternalForm());
            accountStage.setTitle("NewBudget - " + account.name());
            accountStage.setScene(scene);
            accountStage.show();
        } catch (Exception ex) {
            showError("Open Account Failed", "Unable to open account window.");
        }
    }

    private Optional<CategoryType> promptForParentSection() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("Mandatory", List.of("Income", "Mandatory", "Discretionary"));
        dialog.setTitle("Category Section");
        dialog.setHeaderText("Choose section for new parent category");
        dialog.setContentText("Section:");

        Optional<String> selected = dialog.showAndWait();
        if (selected.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(parseParentSection(selected.get()));
    }

    private CategoryType parseParentSection(String section) {
        if ("Income".equalsIgnoreCase(section)) {
            return CategoryType.INCOME;
        }
        if ("Mandatory".equalsIgnoreCase(section)) {
            return CategoryType.MANDATORY;
        }
        return CategoryType.DISCRETIONARY;
    }

    private void onShowHiddenToggled() {
        if (selectedMonth != null) {
            loadMonth(selectedMonth);
        }
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

        if (row != null && row.isRollup() && !row.isMaster()) {
            row.setBudgetAmount(leafBudgetSum);
        }

        return leafBudgetSum;
    }

    static boolean shouldBlankBudgetLikeCell(BudgetTableRow row) {
        return row != null && row.isChildOfMaster();
    }

    static boolean shouldOfferMasterToggle(BudgetTableRow row, TreeItem<BudgetTableRow> treeItem) {
        return row != null && treeItem != null && !treeItem.getChildren().isEmpty();
    }

    static boolean canAddChildCategory(BudgetTableRow row) {
        return row != null && row.getType() != CategoryType.INCOME;
    }

    static boolean canAddParentCategory(BudgetTableRow row) {
        return row != null;
    }

    static boolean isDeleteCategoryEnabled(TreeItem<BudgetTableRow> treeItem) {
        return treeItem != null && treeItem.getChildren().isEmpty();
    }

    private boolean canEditBudget(TreeItem<BudgetTableRow> treeItem) {
        BudgetTableRow row = getCurrentRow(treeItem);
        if (row == null || row.isChildOfMaster()) {
            return false;
        }
        if (row.isMaster()) {
            return true;
        }
        return !row.isRollup();
    }

    private boolean canEditBalance(TreeItem<BudgetTableRow> treeItem) {
        BudgetTableRow row = getCurrentRow(treeItem);
        if (row == null || row.isChildOfMaster()) {
            return false;
        }
        if (row.isMaster()) {
            return true;
        }
        return treeItem != null && treeItem.getChildren().isEmpty();
    }

    private static BudgetTableRow getCurrentRow(TreeItem<BudgetTableRow> treeItem) {
        return treeItem == null ? null : treeItem.getValue();
    }

    private static BudgetTableRow getCurrentRow(TreeTableRow<BudgetTableRow> tableRow) {
        return tableRow == null ? null : tableRow.getItem();
    }

    static boolean canAssignCategoryToAccount(BudgetTableRow row) {
        return row != null && row.getDepth() == 0;
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
            discretionaryBalanceTotal,
            summaryIncomeActual,
            summaryIncomeBudget,
            summaryIncomeDiff,
            summaryIncomeBalance,
            summaryMandatoryActual,
            summaryMandatoryBudget,
            summaryMandatoryDiff,
            summaryMandatoryBalance,
            summaryDiscretionaryActual,
            summaryDiscretionaryBudget,
            summaryDiscretionaryDiff,
            summaryDiscretionaryBalance,
            summaryNetActual,
            summaryNetBudget,
            summaryNetDiff,
            summaryNetBalance
        )) {
            label.setText(ZERO_MONEY);
        }
    }

    static SectionTotals calculateSectionTotals(List<BudgetTableRow> rows) {
        double actualTotal = rows.stream().filter(row -> row.getDepth() == 0).mapToDouble(BudgetTableRow::getActualAmount).sum();
        double budgetTotal = rows.stream().filter(row -> row.getDepth() == 0).mapToDouble(BudgetTableRow::getBudgetAmount).sum();
        double differenceTotal = rows.stream().filter(row -> row.getDepth() == 0).mapToDouble(BudgetTableRow::getDifference).sum();
        double balanceTotal = rows.stream().filter(row -> row.getDepth() == 0).mapToDouble(BudgetTableRow::getBalance).sum();
        return new SectionTotals(actualTotal, budgetTotal, differenceTotal, balanceTotal);
    }

    static SectionTotals calculateNetTotals(
        SectionTotals income,
        SectionTotals mandatory,
        SectionTotals discretionary
    ) {
        return new SectionTotals(
            income.actual() - mandatory.actual() - discretionary.actual(),
            income.budget() - mandatory.budget() - discretionary.budget(),
            income.difference() - mandatory.difference() - discretionary.difference(),
            income.balance() - mandatory.balance() - discretionary.balance()
        );
    }

    private static SectionTotals calculateTableTotals(TreeTableView<BudgetTableRow> table) {
        TreeItem<BudgetTableRow> root = table.getRoot();
        if (root == null) {
            return SectionTotals.ZERO;
        }

        double actualTotal = 0.0;
        double budgetTotal = 0.0;
        double differenceTotal = 0.0;
        double balanceTotal = 0.0;

        for (TreeItem<BudgetTableRow> child : root.getChildren()) {
            BudgetTableRow row = child.getValue();
            if (row == null) {
                continue;
            }
            actualTotal += row.getActualAmount();
            budgetTotal += row.getBudgetAmount();
            differenceTotal += row.getDifference();
            balanceTotal += row.getBalance();
        }

        return new SectionTotals(actualTotal, budgetTotal, differenceTotal, balanceTotal);
    }

    private void setTotals(Label actual, Label budget, Label difference, Label balance, SectionTotals totals) {
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
        actual.setText(money.format(totals.actual()));
        budget.setText(money.format(totals.budget()));
        difference.setText(money.format(totals.difference()));
        balance.setText(money.format(totals.balance()));
    }

    private void refreshTotalsAndSummaryFromTables() {
        SectionTotals incomeTotals = calculateTableTotals(incomeTable);
        SectionTotals mandatoryTotals = calculateTableTotals(mandatoryTable);
        SectionTotals discretionaryTotals = calculateTableTotals(discretionaryTable);
        SectionTotals netTotals = calculateNetTotals(incomeTotals, mandatoryTotals, discretionaryTotals);

        setTotals(incomeActualTotal, incomeBudgetTotal, incomeDifferenceTotal, incomeBalanceTotal, incomeTotals);
        setTotals(mandatoryActualTotal, mandatoryBudgetTotal, mandatoryDifferenceTotal, mandatoryBalanceTotal, mandatoryTotals);
        setTotals(
            discretionaryActualTotal,
            discretionaryBudgetTotal,
            discretionaryDifferenceTotal,
            discretionaryBalanceTotal,
            discretionaryTotals
        );

        setTotals(summaryIncomeActual, summaryIncomeBudget, summaryIncomeDiff, summaryIncomeBalance, incomeTotals);
        setTotals(
            summaryMandatoryActual,
            summaryMandatoryBudget,
            summaryMandatoryDiff,
            summaryMandatoryBalance,
            mandatoryTotals
        );
        setTotals(
            summaryDiscretionaryActual,
            summaryDiscretionaryBudget,
            summaryDiscretionaryDiff,
            summaryDiscretionaryBalance,
            discretionaryTotals
        );
        setTotals(summaryNetActual, summaryNetBudget, summaryNetDiff, summaryNetBalance, netTotals);
    }

    private void installReactiveSummaryListeners() {
        installTableSummaryListeners(incomeTable);
        installTableSummaryListeners(mandatoryTable);
        installTableSummaryListeners(discretionaryTable);
    }

    private void installTableSummaryListeners(TreeTableView<BudgetTableRow> table) {
        table.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            detachTreeListeners(oldRoot);
            attachTreeListeners(newRoot);
            refreshTotalsAndSummaryFromTables();
        });

        if (table.getRoot() != null) {
            attachTreeListeners(table.getRoot());
        }
    }

    private void attachTreeListeners(TreeItem<BudgetTableRow> item) {
        if (item == null) {
            return;
        }

        attachRowListeners(item.getValue());

        if (!treeChildrenListeners.containsKey(item)) {
            ListChangeListener<TreeItem<BudgetTableRow>> childrenListener = change -> {
                while (change.next()) {
                    if (change.wasRemoved()) {
                        for (TreeItem<BudgetTableRow> removed : change.getRemoved()) {
                            detachTreeListeners(removed);
                        }
                    }
                    if (change.wasAdded()) {
                        for (TreeItem<BudgetTableRow> added : change.getAddedSubList()) {
                            attachTreeListeners(added);
                        }
                    }
                }
                refreshTotalsAndSummaryFromTables();
            };
            item.getChildren().addListener(childrenListener);
            treeChildrenListeners.put(item, childrenListener);
        }

        for (TreeItem<BudgetTableRow> child : item.getChildren()) {
            attachTreeListeners(child);
        }
    }

    private void detachTreeListeners(TreeItem<BudgetTableRow> item) {
        if (item == null) {
            return;
        }

        ListChangeListener<TreeItem<BudgetTableRow>> listener = treeChildrenListeners.remove(item);
        if (listener != null) {
            item.getChildren().removeListener(listener);
        }

        detachRowListeners(item.getValue());
        for (TreeItem<BudgetTableRow> child : item.getChildren()) {
            detachTreeListeners(child);
        }
    }

    private void attachRowListeners(BudgetTableRow row) {
        if (row == null || rowBudgetListeners.containsKey(row)) {
            return;
        }

        ChangeListener<Number> budgetListener = (obs, oldValue, newValue) -> refreshTotalsAndSummaryFromTables();
        ChangeListener<Number> balanceListener = (obs, oldValue, newValue) -> refreshTotalsAndSummaryFromTables();

        row.budgetAmountProperty().addListener(budgetListener);
        row.balanceProperty().addListener(balanceListener);

        rowBudgetListeners.put(row, budgetListener);
        rowBalanceListeners.put(row, balanceListener);
    }

    private void detachRowListeners(BudgetTableRow row) {
        if (row == null) {
            return;
        }

        ChangeListener<Number> budgetListener = rowBudgetListeners.remove(row);
        if (budgetListener != null) {
            row.budgetAmountProperty().removeListener(budgetListener);
        }

        ChangeListener<Number> balanceListener = rowBalanceListeners.remove(row);
        if (balanceListener != null) {
            row.balanceProperty().removeListener(balanceListener);
        }
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
            boolean includeHidden = showHiddenToggle.isSelected();
            MonthSnapshot snapshot = accountId == null
                ? budgetService.loadMonth(month, includeHidden)
                : budgetService.loadAccountMonth(accountId, month, includeHidden);
            List<BudgetTableRow> income = snapshot.income().stream().map(BudgetTableRow::new).toList();
            List<BudgetTableRow> mandatory = snapshot.mandatory().stream().map(BudgetTableRow::new).toList();
            List<BudgetTableRow> discretionary = snapshot.discretionary().stream().map(BudgetTableRow::new).toList();

            applySection(incomeTable, income);
            applySection(mandatoryTable, mandatory);
            applySection(discretionaryTable, discretionary);
            refreshTotalsAndSummaryFromTables();

            selectedMonthLabel.setText(accountName == null ? MONTH_DISPLAY.format(month) : accountName + " • " + MONTH_DISPLAY.format(month));
            statusLabel.setText(
                includeHidden
                    ? "Showing " + MONTH_DISPLAY.format(month) + " (including hidden categories)"
                    : "Showing " + MONTH_DISPLAY.format(month)
            );
        } catch (Exception ex) {
            showError("Load failed", ex.getMessage());
        }
    }

    private void updateWindowModeControls() {
        boolean accountMode = accountId != null;
        if (createAccountButton != null) {
            createAccountButton.setVisible(!accountMode);
            createAccountButton.setManaged(!accountMode);
        }
        if (openAccountButton != null) {
            openAccountButton.setVisible(!accountMode);
            openAccountButton.setManaged(!accountMode);
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
        refreshTotalsAndSummaryFromTables();
    }

    record SectionTotals(double actual, double budget, double difference, double balance) {
        private static final SectionTotals ZERO = new SectionTotals(0.0, 0.0, 0.0, 0.0);
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