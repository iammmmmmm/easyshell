package cn.oyzh.easyshell.controller.connect.smb;

import cn.oyzh.common.util.StringUtil;
import cn.oyzh.easyshell.domain.ShellConnect;
import cn.oyzh.easyshell.event.ShellEventUtil;
import cn.oyzh.easyshell.fx.ShellOsTypeComboBox;
import cn.oyzh.easyshell.fx.smb.ShellSMBUserTextField;
import cn.oyzh.easyshell.store.ShellConnectStore;
import cn.oyzh.easyshell.util.ShellConnectUtil;
import cn.oyzh.fx.gui.combobox.CharsetComboBox;
import cn.oyzh.fx.gui.text.field.ClearableTextField;
import cn.oyzh.fx.gui.text.field.NumberTextField;
import cn.oyzh.fx.gui.text.field.PasswordTextField;
import cn.oyzh.fx.gui.text.field.PortTextField;
import cn.oyzh.fx.plus.FXConst;
import cn.oyzh.fx.plus.controller.StageController;
import cn.oyzh.fx.plus.controls.tab.FXTabPane;
import cn.oyzh.fx.plus.controls.text.area.FXTextArea;
import cn.oyzh.fx.plus.information.MessageBox;
import cn.oyzh.fx.plus.window.StageAttribute;
import cn.oyzh.i18n.I18nHelper;
import javafx.fxml.FXML;
import javafx.stage.Modality;
import javafx.stage.WindowEvent;

/**
 * smb连接修改业务
 *
 * @author oyzh
 * @since 2025/07/22
 */
@StageAttribute(
        modality = Modality.APPLICATION_MODAL,
        value = FXConst.FXML_PATH + "connect/smb/shellUpdateSMBConnect.fxml"
)
public class ShellUpdateSMBConnectController extends StageController {

    /**
     * 用户名
     */
    @FXML
    private ShellSMBUserTextField userName;

    /**
     * 密码
     */
    @FXML
    private PasswordTextField password;

    /**
     * tab组件
     */
    @FXML
    private FXTabPane tabPane;

    /**
     * ssh信息
     */
    private ShellConnect shellConnect;

    /**
     * 名称
     */
    @FXML
    private ClearableTextField name;

    /**
     * 备注
     */
    @FXML
    private FXTextArea remark;

    /**
     * 连接ip
     */
    @FXML
    private ClearableTextField hostIp;

    /**
     * 连接端口
     */
    @FXML
    private PortTextField hostPort;

    /**
     * 字符集
     */
    @FXML
    private CharsetComboBox charset;

    /**
     * 连接超时时间
     */
    @FXML
    private NumberTextField connectTimeOut;

    /**
     * 系统类型
     */
    @FXML
    private ShellOsTypeComboBox osType;

    /**
     * 区域
     */
    @FXML
    private ClearableTextField shareName;

    /**
     * ssh连接储存对象
     */
    private final ShellConnectStore connectStore = ShellConnectStore.INSTANCE;

    /**
     * 获取连接地址
     *
     * @return 连接地址
     */
    private String getHost() {
        String hostText;
        String hostIp = this.hostIp.getTextTrim();
        this.tabPane.select(0);
        if (!this.hostPort.validate()) {
            this.tabPane.select(0);
            return null;
        }
        if (!this.hostIp.validate()) {
            this.tabPane.select(0);
            return null;
        }
        hostText = hostIp + ":" + this.hostPort.getValue();
        return hostText;
    }

    /**
     * 测试连接
     */
    @FXML
    private void testConnect() {
        // 检查连接地址
        String host = this.getHost();
        if (StringUtil.isBlank(host) || StringUtil.isBlank(host.split(":")[0])) {
//            MessageBox.warn(I18nHelper.contentCanNotEmpty());
        } else {
            // 创建ssh信息
            ShellConnect shellConnect = new ShellConnect();
            shellConnect.setType("smb");
            shellConnect.setHost(host);
            shellConnect.setConnectTimeOut(3);
            shellConnect.setId(this.shellConnect.getId());
            // 认证信息
            shellConnect.setUser(this.userName.getTextTrim());
            shellConnect.setPassword(this.password.getPassword());
            ShellConnectUtil.testConnect(this.stage, shellConnect);
            // smb独有
            shellConnect.setSmbShareName(this.shareName.getText());
        }
    }

    /**
     * 修改信息
     */
    @FXML
    private void update() {
        String host = this.getHost();
        if (host == null) {
            return;
        }
        String userName = this.userName.getTextTrim();
        if (!this.userName.validate()) {
            return;
        }
        String password = this.password.getPassword();
        String shareName = this.shareName.getTextTrim();
        if (!this.shareName.validate()) {
            return;
        }
        // 名称未填，则直接以host为名称
        if (StringUtil.isBlank(this.name.getTextTrim())) {
            this.name.setText(host.replace(":", "_"));
        }
        try {
            String name = this.name.getTextTrim();
            String remark = this.remark.getTextTrim();
            String osType = this.osType.getSelectedItem();
            String charset = this.charset.getCharsetName();
            int connectTimeOut = this.connectTimeOut.getIntValue();

            this.shellConnect.setName(name);
            this.shellConnect.setOsType(osType);
            this.shellConnect.setRemark(remark);
            this.shellConnect.setCharset(charset);
            this.shellConnect.setHost(host.trim());
            this.shellConnect.setConnectTimeOut(connectTimeOut);
            // 认证信息
            this.shellConnect.setUser(userName.trim());
            this.shellConnect.setPassword(password.trim());
            // smb独有
            this.shellConnect.setSmbShareName(shareName);
            // 保存数据
            if (this.connectStore.replace(this.shellConnect)) {
                ShellEventUtil.connectUpdated(this.shellConnect);
                MessageBox.okToast(I18nHelper.operationSuccess());
                this.closeWindow();
            } else {
                MessageBox.warn(I18nHelper.operationFail());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            MessageBox.exception(ex);
        }
    }

    @Override
    protected void bindListeners() {
        super.bindListeners();
    }

    @Override
    public void onWindowShown(WindowEvent event) {
        super.onWindowShown(event);
        this.shellConnect = this.getProp("shellConnect");
        this.name.setText(this.shellConnect.getName());
        this.hostIp.setText(this.shellConnect.hostIp());
        this.osType.select(this.shellConnect.getOsType());
        this.remark.setText(this.shellConnect.getRemark());
        this.hostPort.setValue(this.shellConnect.hostPort());
        this.charset.setValue(this.shellConnect.getCharset());
        this.connectTimeOut.setValue(this.shellConnect.getConnectTimeOut());
        // 认证处理
        this.userName.setText(this.shellConnect.getUser());
        this.password.setText(this.shellConnect.getPassword());
        // smb独有
        this.shareName.setText(this.shellConnect.getSmbShareName());
        this.stage.switchOnTab();
        this.stage.hideOnEscape();
    }

    @Override
    public String getViewTitle() {
        return I18nHelper.connectUpdateTitle();
    }
}
