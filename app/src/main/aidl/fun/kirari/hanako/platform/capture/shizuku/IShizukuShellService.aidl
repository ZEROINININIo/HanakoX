package fun.kirari.hanako.platform.capture.shizuku;

interface IShizukuShellService {
    byte[] exec(in String[] command);
}
