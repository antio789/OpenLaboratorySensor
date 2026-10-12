package ant.gasmeter.Experiment;

import ant.gasmeter.ApplicationState;
import ant.gasmeter.ApplicationState.TabData;
import ant.gasmeter.BLE_Handler.BLEReceiver;
import javafx.scene.layout.Pane;

public class TabControl {
    public Pane chartPane;
    TabData initData;
    ApplicationState state;

    public void initialise(TabData data){
        initData = data;
        ApplicationState.getInstance();
    }

    public void connect(){
        BLEReceiver BLE = new BLEReceiver(state.getData());
        state.getBleConnections().add(BLE);
        //return BLE.initializeBluetoothConnection(tabValidation.validationLabel(), state.getTabData(tabname).macAddress());
    }
}
