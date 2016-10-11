package please.change.me.common.mail.testsupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nablarch.core.log.basic.LogWriterSupport;

public class OnMemoryLogWriter extends LogWriterSupport {
    
    private static final Map<String, List<String>> messagesMap = new HashMap<String, List<String>>();
    
    public static void clear() {
        messagesMap.clear();
    }
    
    public static List<String> getMessages(String name) {
        if (!messagesMap.containsKey(name)) {
            messagesMap.put(name, new ArrayList<String>());
        }
        return messagesMap.get(name);
    }
    
    protected void onWrite(String formattedMessage) {
        if (formattedMessage.contains("initialized.")) {
            return;
        }
        getMessages(getName()).add(formattedMessage);
    }

    protected void onTerminate() {
        getMessages(getName()).add("@@@END@@@");
    }
}
