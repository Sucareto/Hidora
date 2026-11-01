package app.sucareto.hidora.core.gadget;

interface IGadgetService {
    Bundle rebuild(in Bundle spec);
    Bundle activate(String name);
    Bundle deactivate(String name);
    Bundle remove(String name);
    Bundle queryState();
    Bundle openHid(String name);
    Bundle prepareFfs(in Bundle spec);
    Bundle checkEnvironment();

    int write(long handle, in byte[] data);
    int read(long handle, out byte[] buffer);
    int poll(long handle, int timeoutMs);
    void close(long handle);
}
