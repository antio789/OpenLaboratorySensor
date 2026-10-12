package ant.gasmeter;

import ant.gasmeter.BLE_Handler.BLEReceiver;
import ant.gasmeter.utils.DataManagement;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationState {
    private static ApplicationState instance;

    public record TabValidation(TextField macField, List<TextField> lowerPinFields, List<TextField> upperPinFields, Label validationLabel){}
    public record TabData(String macAddress, List<String> lowerPins, List<String> upperPins) {}
    private TabValidation tabValidation;

    private DataManagement data;
    private final List<BLEReceiver> bleConnections = new ArrayList<>();
    private final Map<String, TabData> TabsData = new HashMap<>();

    private ApplicationState() {}

    public static ApplicationState getInstance() {
        if (instance == null) {
            instance = new ApplicationState();
        }
        return instance;
    }

    // Getters and setters
    public DataManagement getData() { return data; }
    public void setData(DataManagement data) { this.data = data; }
    public List<BLEReceiver> getBleConnections() { return bleConnections; }

    public TabData getTabData(String tabName) { return TabsData.get(tabName); }
    //TRUE if Successfully added
    public boolean setTabData(String tabName, TabData data) {
        if (TabsData.containsKey(tabName)) {
            System.out.println("already present");
            return false;}
        TabsData.put(tabName,data);
        return true;
    }


    public void setValidationData(TabValidation data) {
        tabValidation = data;}
    public TabValidation getValidationData() {return tabValidation;}

}
