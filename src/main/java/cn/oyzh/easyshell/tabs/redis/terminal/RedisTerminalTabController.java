package cn.oyzh.easyshell.tabs.redis.terminal;

import cn.oyzh.easyshell.domain.ShellConnect;
import cn.oyzh.easyshell.redis.RedisClient;
import cn.oyzh.easyshell.terminal.redis.RedisTerminalPane;
import cn.oyzh.fx.gui.tabs.SubTabController;
import cn.oyzh.fx.plus.controls.tab.FXTab;
import javafx.fxml.FXML;

/**
 * redis命令行tab内容组件
 *
 * @author oyzh
 * @since 2023/07/21
 */
public class RedisTerminalTabController extends SubTabController {

    /**
     * 根节点
     */
    @FXML
    private FXTab root;

    /**
     * redis命令行文本域
     */
    @FXML
    private RedisTerminalPane terminal;

    /**
     * redis客户端
     */
    private RedisClient client;

    /**
     * 初始化
     *
     * @param client redis客户端
     */
    public void init(RedisClient client) {
        // this.terminal.init(client, dbIndex);
        this.client = client;
    }

    /**
     * redis信息
     *
     * @return 当前redis信息
     */
    protected ShellConnect shellConnect() {
        return this.terminal.shellConnect();
    }

    public Integer dbIndex() {
        return this.terminal.getDbIndex();
    }

    public RedisClient getClient() {
        return this.terminal.getClient();
    }

    // @Override
    // public void onTabClosed(Event event) {
    //     if (this.terminal.isTemporary()) {
    //         RedisConnectUtil.close(this.client(), true, true);
    //     }
    //     super.onTabClosed(event);
    // }

    /**
     * 初始化标志位
     */
    private boolean initFlag = false;

    @Override
    public void onTabInit(FXTab tab) {
        super.onTabInit(tab);
        this.root.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue && !this.initFlag) {
                this.initFlag = true;
                this.terminal.init(this.client, null);
            }
        });
    }
}
