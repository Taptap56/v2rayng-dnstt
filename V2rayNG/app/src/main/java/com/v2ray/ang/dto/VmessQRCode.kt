package com.v2ray.ang.dto

data class VmessQRCode(
    var v: String = "",
    var ps: String = "",
    var add: String = "",
    var port: String = "",
    var id: String = "",
    var aid: String = "0",
    var scy: String = "",
    var net: String = "",
    var type: String = "",
    var host: String = "",
    var path: String = "",
    var tls: String = "",
    var sni: String = "",
    var alpn: String = "",
    var fp: String = "",
    var insecure: String = "",
    var vcn: String = "",
    var pcs: String = "",

    // DNSTT (SlowDNS) settings, only present when the server is
    // dialed through the tunnel. Nullable so plain Gson omits them
    // from the JSON of a plain server (byte-identical export).
    var dnstt_ns: String? = null,
    var dnstt_pubkey: String? = null,
    var dnstt_resolver: String? = null,
    var dnstt_record_type: String? = null
)