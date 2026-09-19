package ai.yuzu.web;

import ai.yuzu.common.error.SandboxViolationException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.26 🍊 The SSRF guard only lets public http(s) destinations through (no DNS needed for these cases). */
class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard();

    /** v0.0.26 🍊 A public literal address on a normal web port is accepted. */
    @Test
    void acceptsPublicHttpUrl() {
        SsrfGuard.Target target = guard.check("https://93.184.216.34/reports/citrus");
        assertThat(target.host()).isEqualTo("93.184.216.34");
        assertThat(target.port()).isEqualTo(443);
        assertThat(target.addresses()).isNotEmpty();
    }

    /** v0.0.26 🍊 Only http and https are browsable. */
    @Test
    void rejectsOtherSchemes() {
        assertThatThrownBy(() -> guard.check("file:///etc/passwd")).isInstanceOf(SandboxViolationException.class)
                .hasMessageContaining("http");
        assertThatThrownBy(() -> guard.check("ftp://93.184.216.34/pub"))
                .isInstanceOf(SandboxViolationException.class);
        assertThatThrownBy(() -> guard.check("javascript:alert(1)"))
                .isInstanceOf(SandboxViolationException.class);
    }

    /** v0.0.26 🍊 Loopback, private, link-local and cloud-metadata destinations are refused. */
    @Test
    void rejectsPrivateDestinations() {
        for (String url : new String[]{"http://127.0.0.1/admin", "http://localhost:80/", "http://10.1.2.3/",
                "http://192.168.0.5/", "http://172.16.9.9/", "http://169.254.169.254/latest/meta-data/",
                "http://[::1]/", "http://[fd00::1]/", "http://0.0.0.0/", "http://100.64.0.1/",
                "http://[::ffff:127.0.0.1]/"}) {
            assertThatThrownBy(() -> guard.check(url)).describedAs(url)
                    .isInstanceOf(SandboxViolationException.class);
        }
    }

    /** v0.0.26 🍊 Credentials in the URL and unusual ports are refused. */
    @Test
    void rejectsCredentialsAndOddPorts() {
        assertThatThrownBy(() -> guard.check("http://user:pass@93.184.216.34/"))
                .isInstanceOf(SandboxViolationException.class).hasMessageContaining("credentials");
        assertThatThrownBy(() -> guard.check("http://93.184.216.34:22/"))
                .isInstanceOf(SandboxViolationException.class).hasMessageContaining("port");
    }

    /** v0.0.26 🍊 Malformed or relative URLs are refused before any lookup happens. */
    @Test
    void rejectsMalformedUrls() {
        assertThatThrownBy(() -> guard.check("not a url")).isInstanceOf(SandboxViolationException.class);
        assertThatThrownBy(() -> guard.check("/relative/path")).isInstanceOf(SandboxViolationException.class);
        assertThatThrownBy(() -> guard.check("")).isInstanceOf(SandboxViolationException.class);
        assertThatThrownBy(() -> guard.check(null)).isInstanceOf(SandboxViolationException.class);
    }

    /** v0.0.26 🍊 The address rule itself: every kind of non-public address is blocked. */
    @Test
    void blocksNonPublicAddresses() throws Exception {
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("224.0.0.1"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("255.255.255.255"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("198.18.0.1"))).isTrue();
    }
}
