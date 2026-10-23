// keycloak-js loads silent-check-sso.html in a hidden frame and waits for this message
parent.postMessage(location.href, location.origin)
