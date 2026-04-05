package edu.ibu.termotabip.usb

private const val TAG = "UvcFrameGrabber"

// UVC bmRequestType
private const val RT_SET = 0x21
private const val RT_GET = 0xA1

// UVC Request Codes
private const val SET_CUR = 0x01
private const val GET_CUR = 0x81
private const val GET_MIN = 0x82
private const val GET_MAX = 0x83
private const val GET_DEF = 0x87

// VideoStreaming selectors
private const val VS_PROBE_CONTROL  = 0x01
private const val VS_COMMIT_CONTROL = 0x02

