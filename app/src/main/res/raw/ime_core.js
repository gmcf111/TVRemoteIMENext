var isSupportTouch = "ontouchend" in document ? true : false;
var processbar1 = $("#processbar1");
var processbar2 = $("#processbar2");
var processbarApp = $("#processbarApp");
var tabs = $('.tab-content');
var keyActionTimer = null;
var curKeyState = 0;
var curKeyCode = "";
var curPath = "";
var selectedPaths = [];
var selectedPathId = 0;
var fileOperItems = $('.file-oper-items');

function formatSize(size) {
	if (size < 1024) {
		return size + " B";
	} else if (size < (1024 * 1024)) {
		return (size / 1024).toFixed(1) + " KB";
	} else if (size < (1024 * 1024 * 1024)) {
		return (size / (1024 * 1024)).toFixed(1) + " MB";
	} else {
		return (size / (1024 * 1024 * 1024)).toFixed(1) + " GB";
	}
}
function parseTVData(text) {
	var tv = [];
	var lines = text.split('\n');
	var offset = 0;
	while (offset < lines.length) {
		var line = lines[offset].trim();
		if (line.length > 2 && line.substr(0, 1) == "[" && line.substr(line.length - 1, 1) == "]") {
			var name = line.substring(1, line.length - 1);
			var urls = [];
			offset++;
			while (offset < lines.length) {
				line = lines[offset].trim();
				if (line.length > 1) {
					var c = line.substr(0, 1);
					if (c == '[') break;
					if (c != '#') {
						var p = line.indexOf('=');
						if (p > 0) {
							var item = {
								"name": line.substring(0, p).trim(),
								"url": line.substring(p + 1).trim()
							};
							if (item.name.length && item.url.length) urls.push(item);
						}
					}
				}
				offset++;
			}
			if (urls.length) tv.push({ "name": name, "urls": urls });
		} else {
			offset++;
		}
	}
	return tv;
}
function postKeyCode(keyCode) {
	$.post("/key", { code: keyCode }, function (data) {
		console.log(data);
	});
}
function postKeyActionCode(keyCode, keyAction) {
	curKeyCode = keyCode;
	curKeyState = keyAction;
	var action = function () {
		var path = keyAction == 1 ? "/keydown" : "/keyup";
		$.post(path, { code: keyCode }, function (data) {
			console.log(data);
			if (curKeyState == 1 && curKeyCode == keyCode) {
				keyActionTimer = setTimeout(action, 100);
			} else {
				keyActionTimer = null;
			}
		});
	}
	if (keyAction == 2) {
		if (keyActionTimer) {
			clearTimeout(keyActionTimer);
			keyActionTimer = null;
		}
	}
	action();
}
function clickApp(id, type) {
	var app = $("#app-" + id);
	if (2 != type || confirm("是否确认要卸载应用[" + app.text() + "]？")) {
		$.post(1 == type ? "/run" : "/uninstall", { packageName: app.attr("data-packageName") }, function (data) {
			if ("ok" == data && 2 == type) {
				setTimeout(reloadAppList, 15e3);
			}
		});
	}
}
function postFileAction(action) {
	if (selectedPaths.length == 0) return;

	var title = action == "copy" ? "是否确认要将所有选择的目录或者文件复制到当前目录下？" :
		action == "cut" ? "是否确认要将所有选择的目录或者文件剪切到当前目录下？" :
			"是否确认要删除所有选择的目录或者文件？不可恢复！";
	if (confirm(title)) {
		if (action == "delete" && !confirm("请再次确认是否要删除所有选择的目录或者文件？不可恢复！")) return;
		$.post("/file/" + action, { targetPath: curPath, paths: selectedPaths.join('|') }, function (data) {
			if ("ok" == data) {
				selectedPaths = [];
				fileOperItems.empty();
				$('.file-operations').addClass('hidden');
				selectedPathId = 0;
				setTimeout(function () {
					loadFileList(curPath);
				}, 1000);
			}
		});
	}
}
function getDiskSpace() {
	$.get("/sdcard_stat", null, function (data) {
		$('#diskSpace').html('存储总容量：' + formatSize(data.totalBytes) + '，可用容量：' + formatSize(data.availableBytes));
	});
}
function reloadAppList() {
	$.post("/apps", { system: $("#cbListSystem")[0].checked }, function (data) {
		var appList = $(".app-list");
		appList.empty();
		var uninstallChecked = $("#cbUninstall")[0].checked;
		var html = [];
		for (var i = 0; i < data.length; i++) {
			var app = data[i];
			html.push('<div class="app-item">');
			html.push('<img src="/icon/' + app.packageName + '" class="app-icon" />');
			html.push('<div class="app-name' + (app.isSysApp ? " blue" : "") + '" id="app-' + i + '" data-packageName="' + app.packageName + '">' + app.lable + "</div>");
			html.push('<div class="app-btn">');
			if (app.isSysApp) {
				html.push('   <input type="button" value="运行" class="btn" onclick="clickApp(' + i + ', 1);" />');
			} else {
				html.push('   <input type="button" value="运行" class="btn1 app-btn1' + (uninstallChecked ? ' hide' : '') + '" onclick="clickApp(' + i + ', 1);" />');
				html.push('\t  <input type="button" value="卸载" class="btn2 app-btn1' + (uninstallChecked ? '' : ' hide') + '" onclick="clickApp(' + i + ', 2);" />');
			}
			html.push("</div>");
			html.push("</div>");
		}
		for (i = 0; i < 3; i++) {
			html.push('<div class="app-item item-empty"></div>');
		}
		appList.html(html.join("\r\n"));
	});
}
function loadFileList(path) {
	curPath = path;
	$('#curPath').text(curPath == '' ? '/' : curPath);
	$.get("/file/dir/" + encodeURIComponent(path), null, function (data) {
		var fileList = $(".file-list");
		fileList.empty();
		var html = [];
		var fileDeleteChecked = $("#cbFileSelect")[0].checked;
		if (data.parent != undefined) {
			html.push('<div class="file-item"><div class="file-icon-panel">');
			html.push('<img src="/ic_dl_folder.png" class="file-icon" onclick="loadFileList(\'' + data.parent + '\');" />');
			html.push('</div><div class="file-name">..</div>');
			html.push('</div>');
		}
		for (var i = 0; i < data.dirs.length; i++) {
			var file = data.dirs[i];
			html.push('<div class="file-item"><div class="file-icon-panel">');
			html.push('<img src="/ic_dl_folder.png" class="file-icon" onclick="loadFileList(\'' + file.path + '\');" />');
			html.push('</div><div class="file-name">' + file.name + '</div>');
			html.push('<div class="app-btn">');
			html.push('<input type="button" value="选择" class="fbtn2 app-btn1' + (fileDeleteChecked ? '' : ' hide') + '" onclick="addFile(1,\'' + file.name + '\',\'' + file.path + '\');" />');
			html.push("</div>");
			html.push('</div>');
		}
		for (var i = 0; i < data.files.length; i++) {
			var file = data.files[i];
			html.push('<div class="file-item"><div class="file-icon-panel">');
			if (file.isMedia) {
				html.push('<img src="/ic_dl_video.png" class="file-icon" onclick="playMedia(this)" uri="' + file.fullPath + '" />');
			} else {
				html.push('<img src="/ic_dl_other.png" class="file-icon" />');
			}
			html.push('<div class="' + (file.isMedia ? 'media-size' : 'file-size') + '">' + formatSize(file.size) + '</div>');
			html.push('</div><div class="file-name">' + file.name + '</div>');
			html.push('<div class="app-btn">');
			html.push('<a href="/file/download/' + file.path + '" target="_blank" class="' + (fileDeleteChecked ? ' hide' : '') + '">');
			html.push('<input type="button" value="下载" class="fbtn1 app-btn1" />');
			html.push("</a>");
			html.push('\t  <input type="button" value="选择" class="fbtn2 app-btn1' + (fileDeleteChecked ? '' : ' hide') + '" onclick="addFile(2,\'' + file.name + '\',\'' + file.path + '\');" />');
			html.push("</div>");
			html.push('</div>');
		}
		for (i = 0; i < 4; i++) {
			html.push('<div class="file-item item-empty"></div>');
		}
		fileList.html(html.join("\r\n"));
	});
}
function addFile(type, name, path) {
	for (var i = 0; i < selectedPaths.length; i++) {
		if (selectedPaths[i] == path) return;
	}
	selectedPaths.push(path);
	selectedPathId++;
	var html = [];
	html.push('<div class="file-oper-item" onclick="removeFile(' + selectedPathId + ',\'' + path + '\');" id="fileOperItem' + selectedPathId + '">');
	html.push('<div class="file-oper-name">');
	html.push(type == 1 ? "目录" : "文件");
	html.push("：");
	html.push(name);
	html.push('</div><div class="file-oper-del"><svg class="md-ic" viewBox="0 -960 960 960"><path d="M480-424 284-228q-11 11-28 11t-28-11q-11-11-11-28t11-28l196-196-196-196q-11-11-11-28t11-28q11-11 28-11t28 11l196 196 196-196q11-11 28-11t28 11q11 11 11 28t-11 28L536-480l196 196q11 11 11 28t-11 28q-11 11-28 11t-28-11L480-424Z"/></svg></div></div>');
	fileOperItems.append(html.join(''));
	$('.file-operations').removeClass('hidden');
}
function removeFile(id, path) {
	var rid = -1;
	for (var i = 0; i < selectedPaths.length; i++) {
		if (selectedPaths[i] == path) {
			rid = i;
			break;
		}
	}
	if (rid != -1) {
		selectedPaths.splice(rid, 1);
	}
	$('#fileOperItem' + id).remove();
	if (selectedPaths.length == 0) {
		selectedPathId = 0;
		$('.file-operations').addClass('hidden');
	}
}
function loadTVList() {
	$.get("/tv.txt", null, function (text) {
		var tvItems = $(".tv-items");
		tvItems.empty();
		$('#tvData').val(text);
		var data = parseTVData(text);
		var html = [];
		for (var i = 0; i < data.length; i++) {
			var tv = data[i];
			html.push('<div class="tv-item">');
			html.push(tv.name);
			html.push('<br />');
			for (var j = 0; j < tv.urls.length; j++) {
				html.push('<a class="tv-source" data-video="' + tv.urls[j].url + '" onclick="playTV(this)">' + tv.urls[j].name + '</a>');
			}
			html.push('</div>');
		}
		tvItems.html(html.join("\r\n"));
	}, "text");
}
$("#confirm").on("click", function () {
	var $input = $("#inputarea");
	var text = $input.val();
	if ("" != text) {
		$input.val("");
		$.post("/text", { text: text }, function (data) {
			console.log(data);
		});
	}
})
$("#cbUninstall").on("click", function () {
	if (this.checked) {
		$(".btn1").addClass("hide");
		$(".btn2").removeClass("hide");
	}
	else {
		$(".btn1").removeClass("hide");
		$(".btn2").addClass("hide");
	}
})
// 直播源编辑（bug 修复：.tv-list/.tv-edit → .tv-section/.tv-editor）
$('#btnShowTVEdit,#btnTVEdit,#btnTVCancel').on('click', function () {
	switch (this.id) {
		case 'btnShowTVEdit':
			$(".tv-section").addClass("hidden");
			$(".tv-editor").removeClass("hidden");
			break;
		case 'btnTVCancel':
			$(".tv-editor").addClass("hidden");
			$(".tv-section").removeClass("hidden");
			break;
		case 'btnTVEdit':
			$.post("/tv.txt", { text: $('#tvData').val() }, function (data) {
				console.log(data);
				if (data == "ok") {
					alert('电视直播源已修改成功！');
					loadTVList();
				} else {
					alert('电视直播源修改失败！');
				}
				$(".tv-editor").addClass("hidden");
				$(".tv-section").removeClass("hidden");
			});
			break;
	}
});
$("#cbFileSelect").on("click", function () {
	if (this.checked) {
		$(".fbtn1").addClass("hide");
		$(".fbtn2").removeClass("hide");
	}
	else {
		$(".fbtn1").removeClass("hide");
		$(".fbtn2").addClass("hide");
	}
})
// Tab切换
$("button.tab, div.tab").on("click", function () {
	var o = $(this);
	$(".tab.active, .cur").removeClass("active cur");
	tabs.addClass("hidden");
	var tabName = o.attr('data-tab') || o.attr('data-rel');
	tabs.filter('[data-tab="' + tabName + '"]').removeClass("hidden");
	o.addClass('active');
})
$("#btnCls").on("click", function () {
	postKeyCode($(this).attr("data-key"))
})
// 方向键 - 长按重复发送（MD3 D-pad：雪碧图背景定位逻辑已随 keys.png 一并移除）
$(".direction-btn, .direction, #btnDel").on(isSupportTouch ? "touchstart" : "mousedown", function () {
	var o = $(this);
	postKeyActionCode(o.attr("data-key"), 1);
	console.log("onkeydown:" + o.attr("data-key"));
})
$(".direction-btn, .direction, #btnDel").on(isSupportTouch ? "touchend" : "mouseup", function () {
	var o = $(this);
	postKeyActionCode(o.attr("data-key"), 2);
	console.log("onkeyup:" + o.attr("data-key"));
})
// 控制按钮（返回、菜单、主页、音量等）- 单次按键
$(".control-btn").on("click", function () {
	var o = $(this);
	var keyCode = o.attr("data-key");
	console.log("control-btn click:" + keyCode);
	postKeyCode(keyCode);
})
$("#cbListSystem").on("click", reloadAppList);
$("#showSettings").on("click", function () {
	$.post("/runSystem", {
		packageName: 'android.settings.SETTINGS'
	}, function (data) {
		console.log(data)
	})
})
$("#btnPlay").on("click", function () {
	var url = $('#playUrl').val();
	if (url.length > 0 && (url.indexOf('http://') == 0
		|| url.indexOf('https://') == 0
		|| url.indexOf('thunder://') == 0
		|| url.indexOf('ed2k://') == 0
		|| url.indexOf('ftp://') == 0
		|| url.indexOf('rtmp://') == 0
		|| url.indexOf('rtmps://') == 0
		|| url.indexOf('mms://') == 0)) {
		$.post("/play", { playUrl: url, "useSystem": $('#playUseSystem')[0].checked }, function (data) {
			console.log(data)
		})
	} else {
		alert('请输入正确的网络视频地址，只支持http/ftp/thunder/ed2k/rtmp/mms。');
	}
})
function playMedia(obj) {
	$.post("/play", { playUrl: $(obj).attr('uri'), "useSystem": $('#playUseSystem')[0].checked }, function (data) {
		console.log(data)
	})
}
$('#stopPlay').on("click", function () {
	$.post("/playStop", null, function (data) {
		console.log(data)
	})
});
$('#playUseSystem').on("click", function () {
	if (this.checked) {
		alert('调用外部播放器不会自动结束边下边播任务，结束播放后请手动点击停止！');
	}
	var time = new Date(9998, 1, 1);
	if (!this.checked) time = new Date(1900, 1, 1);
	document.cookie = "playUseSystem=" + (this.checked ? "1" : "0") + "; expires=" + time.toGMTString();
});
$(function () {
	$('#playUseSystem')[0].checked = document.cookie.indexOf('playUseSystem=1') != -1;
});

$('#speedInterval').on("change", function () {
	$.post("/changePlayFFI", { speedInterval: this.value }, function (data) {
		console.log(data)
	})
});
function playTV(o) {
	$.post("/play", { playUrl: $(o).attr('data-video'), "useSystem": $('#playUseSystem')[0].checked }, function (data) {
		console.log(data)
	})
}
$("#btnClear").on("click", function () {
	if (confirm("是否要删除所有传送的文件？")) {
		$.post("/clearCache", null, function (data) {
			console.log(data);
			alert("传送的文件都已清除完毕！")
		})
	}
})

// ==================== 文件上传（label 模式：选择即上传） ====================

function setProgress(processbar, percentText) {
	if (!processbar || !processbar.length) return;
	processbar.css({ width: percentText });
	processbar.closest('.progress-wrap').find('.progress-text').text(percentText);
}

// 修复：app tab 独立 ID（原版重复 id="upfile"/processbar1 导致应用 Tab 上传失效）；$(id).val 笔误
$("#upfile,#upfile2,#upfile3,#upfileApp,#upfileTV").change(function () {
	var id = this.id;
	var formData = new FormData;
	var file = this.files[0];
	if (!file) return;
	var processbar = null;
	if (id == "upfile") $("#upfile-name").text(file.name);
	if (id == "upfileApp") $("#upfileApp-name").text(file.name);
	if (id == "upfile2") {
		formData.append("path", curPath);
		processbar = processbar2;
	} else if (id == "upfile" || id == "upfileApp") {
		formData.append("autoInstall", $('#cbAutoInstall')[0].checked);
		formData.append("useSystem", $('#playUseSystem')[0].checked);
		processbar = (id == "upfile") ? processbar1 : processbarApp;
	}
	formData.append("file", file, encodeURI(file.uploadName || file.name));
	// upfileTV：上传直播源文件 → POST /tv.txt（multipart，服务端整体覆盖 tv.txt）
	var url = (id == "upfile2") ? "/file/upload"
		: ((id == "upfile3") ? "/torrent/upload"
			: (id == "upfileTV" ? "/tv.txt" : "/upload"));
	$.ajax({
		type: "POST",
		url: url,
		dataType: "json",
		data: formData,
		processData: false,
		contentType: false,
		xhr: function () {
			var xhr = $.ajaxSettings.xhr();
			if (xhr.upload) {
				xhr.upload.addEventListener("progress", function (e) {
					if (processbar) {
						setProgress(processbar, Math.floor(100 * e.loaded / e.total) + "%");
					}
					if (e.loaded == e.total) $("#" + id).val("");
				}, false);
			}
			return xhr;
		},
		beforeSend: function () {
			if (processbar) {
				setProgress(processbar, "1%");
				processbar.closest('.progress-wrap').find('.progress-text').text("");
			}
		},
		success: function (data) {
			if (data.success) {
				if (id == "upfile2") {
					loadFileList(curPath);
					alert("文件已成功上传到当前目录。");
				} else if (id == "upfile3") {
					alert("种子文件已上传并解析，请选择要播放的视频文件。");
					addTorrentItems(data);
				} else if (id == "upfileTV") {
					loadTVList();
					alert("直播源文件已上传并生效。");
				} else {
					if ($('#cbAutoInstall')[0].checked && file.name.indexOf(".apk") != -1) {
						alert("APK包已传送到TV盒子并执行安装，请留意TV屏幕的安装请求。");
					} else {
						alert("文件已成功传送到TV盒子，存储于：" + data.filePath);
					}
				}
			} else {
				alert("抱歉，文件上传失败！");
			}
		}
	});
});
$('#btnPlayTorrent').on('click', function () {
	var torrentItems = $("#torrentItems");
	var videoIndex = torrentItems.val();
	if (videoIndex != '') {
		$.post('/torrent/play', { "videoIndex": videoIndex, "useSystem": $('#playUseSystem')[0].checked }, function (data) {
			console.log(data);
		});
	}
});
function addTorrentItems(data) {
	var torrentItems = $("#torrentItems");
	torrentItems.empty();
	if (data.files && data.files.length) {
		for (var i = 0; i < data.files.length; i++) {
			var f = data.files[i];
			torrentItems.append("<option value='" + f.index + "'>" + f.name + "(" + formatSize(f.size) + ")</option>");
		}
		torrentItems.val(data.files[0].index);
	}
}
function loadTorrentItems() {
	$.post('/torrent/data', null, function (data) {
		if (data.success) addTorrentItems(data);
	});
}

function upgrade() {
	$.get('/version', function (version) {
		$('#curVer').html(version);
	});
}

// ==================== 通道状态与截屏（Root 增强，阶段 4） ====================

var channelInfoCache = null;

function renderChannelBadges(info) {
	var badges = $('#channel-badges');
	if (badges.length === 0) return;
	var names = { root: 'Root', ime: 'IME', a11y: '辅助', adb: 'ADB' };
	var html = [];
	html.push('<span class="badge-title">通道：</span>');
	['root', 'ime', 'a11y', 'adb'].forEach(function (id) {
		var ch = info[id] || {};
		var on = ch.available === true;
		html.push('<span class="channel-badge ' + (on ? 'on' : 'off') + '">' + (names[id] || id) + '</span>');
	});
	badges.html(html.join(''));
}

function refreshChannelInfo() {
	$.get('/channel-info', function (info) {
		channelInfoCache = info;
		renderChannelBadges(info);
		var rootAvailable = info.root && info.root.available === true;
		if (rootAvailable) {
			$('#screenshot-panel').show();
		} else {
			$('#screenshot-panel').hide();
			if (info.root && info.root.enabled === true && rootAvailable !== true) {
				$('#screenshotHint').text('Root 已开启但 su 不可用，截屏不可用');
			} else {
				$('#screenshotHint').text('未开启 Root 增强，如需截屏请在盒子端应用主页开启');
			}
		}
	}).fail(function () {
		$('#screenshot-panel').hide();
	});
}

var screenshotTimer = null;

function takeScreenshot() {
	var ts = new Date().getTime();
	$('#screenshotImg').attr('src', '/screenshot?t=' + ts).show();
	$('#screenshotHint').text('');
}

function setupScreenshot() {
	$('#btnScreenshot').on('click', takeScreenshot);
	$('#cbAutoRefresh').on('change', function () {
		if (this.checked) {
			screenshotTimer = setInterval(takeScreenshot, 2000);
			takeScreenshot();
		} else {
			if (screenshotTimer) { clearInterval(screenshotTimer); screenshotTimer = null; }
		}
	});
}

// ==================== 主题色（04 文档 5.4：网页端只读跟随盒子端；tonal 算法与 Android 端 TonalPalette.kt 一致） ====================

// ---- HSV 工具（TonalPalette.kt 的 JS 移植） ----
function hexToHsv(hex) {
	hex = String(hex).replace('#', '');
	if (hex.length === 3) hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
	var r = parseInt(hex.substr(0, 2), 16) / 255;
	var g = parseInt(hex.substr(2, 2), 16) / 255;
	var b = parseInt(hex.substr(4, 2), 16) / 255;
	var max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min, h = 0;
	if (d > 0) {
		if (max === r) h = ((g - b) / d) % 6;
		else if (max === g) h = (b - r) / d + 2;
		else h = (r - g) / d + 4;
		h *= 60;
		if (h < 0) h += 360;
	}
	var s = max === 0 ? 0 : d / max;
	return { h: h, s: s, v: max };
}
function hsvToHex(h, s, v) {
	var c = v * s, hp = h / 60, x = c * (1 - Math.abs(hp % 2 - 1));
	var r = 0, g = 0, b = 0;
	if (hp < 1) { r = c; g = x; }
	else if (hp < 2) { r = x; g = c; }
	else if (hp < 3) { g = c; b = x; }
	else if (hp < 4) { g = x; b = c; }
	else if (hp < 5) { r = x; b = c; }
	else { r = c; b = x; }
	var m = v - c;
	function t(n) {
		var val = Math.round((n + m) * 255);
		val = Math.max(0, Math.min(255, val));
		var s2 = val.toString(16);
		return s2.length < 2 ? '0' + s2 : s2;
	}
	return '#' + t(r) + t(g) + t(b);
}
function toneOf(hsv, tone) {
	var t = Math.max(0, Math.min(100, tone));
	var s = hsv.s;
	if (t >= 90) s *= 0.45;
	else if (t >= 80) s *= 0.62;
	else if (t >= 70) s *= 0.78;
	else if (t <= 10) s *= 0.30;
	else if (t <= 20) s *= 0.55;
	var v = Math.max(0.02, Math.min(0.98, t / 100));
	return hsvToHex(hsv.h, Math.max(0, Math.min(1, s)), v);
}
function hexToRgbTriplet(hex) {
	hex = String(hex).replace('#', '');
	if (hex.length === 3) hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
	return parseInt(hex.substr(0, 2), 16) + ' ' + parseInt(hex.substr(2, 2), 16) + ' ' + parseInt(hex.substr(4, 2), 16);
}

var THEME_VARS = [
	'--theme-primary', '--theme-on-primary', '--theme-primary-container', '--theme-on-primary-container',
	'--theme-secondary-container', '--theme-on-secondary-container', '--theme-primary-rgb',
	'--theme-dark-primary', '--theme-dark-on-primary', '--theme-dark-primary-container', '--theme-dark-on-primary-container',
	'--theme-dark-secondary-container', '--theme-dark-on-secondary-container', '--theme-dark-primary-rgb'
];

// bug 修复：原版写入 --primary/--accent/--primary-color，CSS 从未引用 → 选色不生效
function applyWebTheme(primaryHex) {
	if (!primaryHex) return;
	var hsv = hexToHsv(primaryHex);
	var root = document.documentElement.style;
	// Light scheme（primary=40 / container=90 / onContainer=10 / secondaryContainer=92）
	root.setProperty('--theme-primary', toneOf(hsv, 40));
	root.setProperty('--theme-on-primary', toneOf(hsv, 100));
	root.setProperty('--theme-primary-container', toneOf(hsv, 90));
	root.setProperty('--theme-on-primary-container', toneOf(hsv, 10));
	root.setProperty('--theme-secondary-container', toneOf(hsv, 92));
	root.setProperty('--theme-on-secondary-container', toneOf(hsv, 10));
	root.setProperty('--theme-primary-rgb', hexToRgbTriplet(toneOf(hsv, 40)));
	// Dark scheme（primary=80 / onPrimary=20 / container=30 / onContainer=90 / secondaryContainer=25）
	root.setProperty('--theme-dark-primary', toneOf(hsv, 80));
	root.setProperty('--theme-dark-on-primary', toneOf(hsv, 20));
	root.setProperty('--theme-dark-primary-container', toneOf(hsv, 30));
	root.setProperty('--theme-dark-on-primary-container', toneOf(hsv, 90));
	root.setProperty('--theme-dark-secondary-container', toneOf(hsv, 25));
	root.setProperty('--theme-dark-on-secondary-container', toneOf(hsv, 90));
	root.setProperty('--theme-dark-primary-rgb', hexToRgbTriplet(toneOf(hsv, 80)));
	// 移动端浏览器地址栏配色
	var meta = document.querySelector('meta[name="theme-color"]');
	if (meta) meta.setAttribute('content', toneOf(hsv, 40));
}

function clearWebTheme() {
	var root = document.documentElement.style;
	THEME_VARS.forEach(function (v) { root.removeProperty(v); });
	var meta = document.querySelector('meta[name="theme-color"]');
	if (meta) meta.setAttribute('content', '#0061A4');
}

// 网页端只读跟随盒子端主题色（用户决策 2026-10：网页端不设选色入口）
function syncThemeFromBox() {
	$.get('/theme', function (t) {
		if (t && t.primary) applyWebTheme(t.primary);
		else clearWebTheme();
	}).fail(function () {
		// 离线/静态预览时静默保持默认色板
	});
}

// ==================== 深浅色模式切换（data-theme 驱动，持久化 localStorage） ====================

function currentMode() {
	return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
}

function setMode(mode) {
	document.documentElement.setAttribute('data-theme', mode);
	localStorage.setItem('next_mode', mode);
	var meta = document.querySelector('meta[name="theme-color"]');
	if (meta) meta.setAttribute('content', mode === 'dark' ? '#121316' : '#FDFCFF');
}

function setupModeToggle() {
	$('#btnMode').on('click', function () {
		setMode(currentMode() === 'dark' ? 'light' : 'dark');
	});
	// 系统偏好变化且用户未手动选择时跟随
	if (window.matchMedia) {
		var mq = matchMedia('(prefers-color-scheme: dark)');
		var onChange = function () {
			if (!localStorage.getItem('next_mode')) setMode(mq.matches ? 'dark' : 'light');
		};
		if (mq.addEventListener) mq.addEventListener('change', onChange);
		else if (mq.addListener) mq.addListener(onChange);
	}
}

// ==================== MD3 Ripple ====================

function initRipple() {
	var selector = '.btn-primary,.btn-secondary,.btn-danger,.btn-warning,.file-btn,.tab,' +
		'.dpad-btn,.dpad-action-btn,.mouse-btn,.swipe-btn,.tv-source,.btn,.btn1,.btn2,.fbtn1,.fbtn2';
	$(document).on(isSupportTouch ? 'touchstart' : 'mousedown', selector, function (e) {
		var el = $(this);
		if (!this.getBoundingClientRect) return;
		var rect = this.getBoundingClientRect();
		var size = Math.max(rect.width, rect.height);
		var x = rect.width / 2 - size / 2;
		var y = rect.height / 2 - size / 2;
		var oe = e.originalEvent || e;
		if (oe.touches && oe.touches.length) {
			x = oe.touches[0].clientX - rect.left - size / 2;
			y = oe.touches[0].clientY - rect.top - size / 2;
		} else if (typeof oe.clientX === 'number' && (oe.clientX !== 0 || oe.clientY !== 0)) {
			x = oe.clientX - rect.left - size / 2;
			y = oe.clientY - rect.top - size / 2;
		}
		var ink = $('<span class="ripple-ink"></span>').css({
			width: size, height: size, left: x, top: y
		});
		el.append(ink);
		setTimeout(function () { ink.remove(); }, 560);
	});
}

// ==================== 语言切换（中/英，用户决策 D9） ====================

var LANG = {
	zh: {
		'输入遥控': '输入遥控', '触摸板': '触摸板', '应用管理': '应用管理', '文件管理': '文件管理', '视频直播': '视频直播',
		'输入': '输入', '退格': '退格', '清空': '清空', '清除缓存': '清除缓存',
		'截屏': '截屏', '每2秒刷新': '每2秒刷新', '应用': '应用', '默认': '默认',
		'停止边下边播': '停止边下边播', '远程播放': '远程播放', '开始播放': '开始播放',
		'修改': '修改', '取消': '取消', '复制': '复制', '剪切': '剪切', '删除': '删除',
		'传送后自动安装APK或播放视频': '传送后自动安装APK或播放视频',
		'显示系统应用': '显示系统应用', '卸载应用': '卸载应用', '选择': '选择',
		'调用系统播放器': '调用系统播放器', '系统设置': '系统设置', '上传直播源': '上传直播源',
		'电视节目：': '电视节目：', '需要更改直播源？': '需要更改直播源？', '点击这里': '点击这里'
	},
	en: {
		'输入遥控': 'Remote', '触摸板': 'Touchpad', '应用管理': 'Apps', '文件管理': 'Files', '视频直播': 'Video',
		'输入': 'Send', '退格': 'Backspace', '清空': 'Clear', '清除缓存': 'Clear Cache',
		'截屏': 'Screenshot', '每2秒刷新': 'Refresh 2s', '应用': 'Apply', '默认': 'Default',
		'停止边下边播': 'Stop P2P', '远程播放': 'Play', '开始播放': 'Play',
		'修改': 'Save', '取消': 'Cancel', '复制': 'Copy', '剪切': 'Cut', '删除': 'Delete',
		'传送后自动安装APK或播放视频': 'Auto-install APK / play video after upload',
		'显示系统应用': 'Show system apps', '卸载应用': 'Uninstall mode', '选择': 'Select',
		'调用系统播放器': 'Use system player', '系统设置': 'System settings', '上传直播源': 'Upload sources',
		'电视节目：': 'TV channels:', '需要更改直播源？': 'Change channel source?', '点击这里': 'click here'
	}
};
var currentLang = localStorage.getItem('next_lang');
if (!currentLang) currentLang = (navigator.language || 'zh').toLowerCase().indexOf('zh') === 0 ? 'zh' : 'en';

var I18N_SELECTOR = '.tab-text,#confirm,#btnDel,#btnCls,#btnClear,#btnScreenshot,#showSettings>span,' +
	'.file-btn>span,#stopPlay,#btnPlay,#btnPlayTorrent,#btnTVEdit,#btnTVCancel,#btnCopy,#btnCut,#btnDelete,.checkbox-label>span';

function applyLang() {
	var dict = LANG[currentLang] || LANG.zh;
	$(I18N_SELECTOR).each(function () {
		var el = $(this);
		var orig = el.data('orig');
		if (!orig) { orig = $.trim(el.text()); el.data('orig', orig); }
		if (dict[orig] !== undefined) el.text(dict[orig]);
	});
	$('#btnLang').text(currentLang === 'zh' ? 'EN' : '中文');
	localStorage.setItem('next_lang', currentLang);
}

function setupLang() {
	$('#btnLang').on('click', function () {
		currentLang = currentLang === 'zh' ? 'en' : 'zh';
		applyLang();
	});
	applyLang();
}

reloadAppList();
loadFileList("");
getDiskSpace();
loadTVList();
loadTorrentItems();
upgrade();
setupScreenshot();
syncThemeFromBox();
setupModeToggle();
setupLang();
refreshChannelInfo();
initRipple();
setInterval(function () {
	upgrade()
}, 18e5);

// ==================== 触摸板功能 ====================

var touchpad = {
	element: null,
	tracking: false,
	lastX: 0,
	lastY: 0,
	sensitivity: 1.5,
	swipeDistance: 300,
	longPressDuration: 600,
	fingers: 0,
	tapStartTime: 0,
	tapStartX: 0,
	tapStartY: 0,
	moveThreshold: 10,  // 移动阈值，超过此值不算点击
	hasMoved: false,
	lastMoveTime: 0,
	throttleInterval: 16  // 约60fps的节流
};

// 初始化触摸板
function initTouchpad() {
	touchpad.element = document.getElementById('touchpad');
	if (!touchpad.element) return;

	// 触摸事件
	touchpad.element.addEventListener('touchstart', onTouchpadStart, { passive: false });
	touchpad.element.addEventListener('touchmove', onTouchpadMove, { passive: false });
	touchpad.element.addEventListener('touchend', onTouchpadEnd, { passive: false });
	touchpad.element.addEventListener('touchcancel', onTouchpadEnd, { passive: false });

	// 鼠标事件 (用于PC端测试)
	touchpad.element.addEventListener('mousedown', onTouchpadStart);
	touchpad.element.addEventListener('mousemove', onTouchpadMove);
	touchpad.element.addEventListener('mouseup', onTouchpadEnd);
	touchpad.element.addEventListener('mouseleave', onTouchpadEnd);

	// 阻止右键菜单
	touchpad.element.addEventListener('contextmenu', function (e) {
		e.preventDefault();
	});

	// 鼠标按钮
	$('#mouse-left').on('click touchend', function (e) {
		e.preventDefault();
		mouseClick(0);
	});
	$('#mouse-longclick').on('click touchend', function (e) {
		e.preventDefault();
		mouseLongClick();
	});
	$('#mouse-right').on('click touchend', function (e) {
		e.preventDefault();
		mouseClick(1);
	});

	// 上划下划按钮
	$('#swipe-up').on('click touchend', function (e) {
		e.preventDefault();
		mouseSwipeUp();
	});
	$('#swipe-down').on('click touchend', function (e) {
		e.preventDefault();
		mouseSwipeDown();
	});

	// 灵敏度调节
	$('#sensitivity').on('input', function () {
		touchpad.sensitivity = parseFloat(this.value);
		$('#sensitivity-value').text(touchpad.sensitivity.toFixed(1));
	});

	// 滑动距离调节
	$('#swipe-distance').on('input', function () {
		touchpad.swipeDistance = parseInt(this.value);
		$('#swipe-distance-value').text(touchpad.swipeDistance);
	});

	// 长按时间调节
	$('#longpress-duration').on('input', function () {
		touchpad.longPressDuration = parseInt(this.value);
		$('#longpress-duration-value').text(touchpad.longPressDuration);
	});

	console.log('Touchpad initialized');
}

// 获取事件坐标
function getEventPos(e) {
	if (e.touches && e.touches.length > 0) {
		return { x: e.touches[0].clientX, y: e.touches[0].clientY };
	} else if (e.changedTouches && e.changedTouches.length > 0) {
		return { x: e.changedTouches[0].clientX, y: e.changedTouches[0].clientY };
	} else {
		return { x: e.clientX, y: e.clientY };
	}
}

// 触摸开始
function onTouchpadStart(e) {
	e.preventDefault();
	var pos = getEventPos(e);
	touchpad.tracking = true;
	touchpad.lastX = pos.x;
	touchpad.lastY = pos.y;
	touchpad.tapStartX = pos.x;
	touchpad.tapStartY = pos.y;
	touchpad.tapStartTime = Date.now();
	touchpad.hasMoved = false;
	touchpad.fingers = e.touches ? e.touches.length : 1;
}

// 触摸移动
function onTouchpadMove(e) {
	if (!touchpad.tracking) return;
	e.preventDefault();

	// 节流
	var now = Date.now();
	if (now - touchpad.lastMoveTime < touchpad.throttleInterval) return;
	touchpad.lastMoveTime = now;

	var pos = getEventPos(e);
	var dx = (pos.x - touchpad.lastX) * touchpad.sensitivity;
	var dy = (pos.y - touchpad.lastY) * touchpad.sensitivity;

	// 检查是否超过移动阈值
	var totalMove = Math.abs(pos.x - touchpad.tapStartX) + Math.abs(pos.y - touchpad.tapStartY);
	if (totalMove > touchpad.moveThreshold) {
		touchpad.hasMoved = true;
	}

	// 更新手指数量
	if (e.touches) {
		touchpad.fingers = e.touches.length;
	}

	if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) {
		if (touchpad.fingers >= 2) {
			// 双指滚动
			mouseScroll(Math.round(dy));
		} else {
			// 单指移动
			mouseMove(Math.round(dx), Math.round(dy));
		}
	}

	touchpad.lastX = pos.x;
	touchpad.lastY = pos.y;
}

// 触摸结束
function onTouchpadEnd(e) {
	if (!touchpad.tracking) return;
	e.preventDefault();

	var tapDuration = Date.now() - touchpad.tapStartTime;

	// 快速点击且没有明显移动 = 点击
	if (tapDuration < 300 && !touchpad.hasMoved) {
		if (touchpad.fingers >= 2) {
			mouseClick(1);  // 双指 = 右键
		} else {
			mouseClick(0);  // 单指 = 左键
		}
	}

	touchpad.tracking = false;
	touchpad.fingers = 0;
}

// 发送鼠标移动
function mouseMove(dx, dy) {
	if (dx === 0 && dy === 0) return;
	$.post("/mouse/move", { dx: dx, dy: dy }, function (data) {
		if (data && String(data).indexOf('ok:') === 0) {
			var pos = String(data).substring(3).split(',');
			$('#mouse-pos').text('位置: ' + pos[0] + ', ' + pos[1]);
		}
	});
}

// 发送鼠标点击
function mouseClick(button) {
	$.post("/mouse/click", { button: button }, function (data) {
		console.log('click:', data);
		if (data === 'ok') {
			// 视觉反馈
			var btnId = button === 0 ? '#mouse-left' : (button === 1 ? '#mouse-right' : '#mouse-middle');
			$(btnId).css('transform', 'scale(0.95)');
			setTimeout(function () {
				$(btnId).css('transform', '');
			}, 100);
		} else if (data && String(data).indexOf('error:') === 0) {
			$('#adb-status').text('ADB: 未连接').removeClass('connected').addClass('disconnected');
		}
	});
}

// 发送鼠标滚动
function mouseScroll(dy) {
	if (dy === 0) return;
	$.post("/mouse/scroll", { dy: dy }, function (data) {
		console.log('scroll:', data);
	});
}

// 发送上划手势
function mouseSwipeUp() {
	$.post("/mouse/swipeup", { distance: touchpad.swipeDistance }, function (data) {
		console.log('swipe up:', data);
		if (data && data.status === 'ok') {
			$('#swipe-up').css('transform', 'scale(0.95)');
			setTimeout(function () {
				$('#swipe-up').css('transform', '');
			}, 100);
		}
	});
}

// 发送下划手势
function mouseSwipeDown() {
	$.post("/mouse/swipedown", { distance: touchpad.swipeDistance }, function (data) {
		console.log('swipe down:', data);
		if (data && data.status === 'ok') {
			$('#swipe-down').css('transform', 'scale(0.95)');
			setTimeout(function () {
				$('#swipe-down').css('transform', '');
			}, 100);
		}
	});
}

// 发送长按
function mouseLongClick() {
	$.post("/mouse/longclick", { duration: touchpad.longPressDuration }, function (data) {
		console.log('long click:', data);
		if (data && data.status === 'ok') {
			$('#mouse-longclick').css('transform', 'scale(0.95)');
			setTimeout(function () {
				$('#mouse-longclick').css('transform', '');
			}, 100);
		}
	});
}

// 检查 ADB 状态
function checkAdbStatus() {
	$.post("/mouse/move", { dx: 0, dy: 0 }, function (data) {
		if (data && String(data).indexOf('ok:') === 0) {
			$('#adb-status').text('ADB: 已连接').removeClass('disconnected').addClass('connected');
		} else {
			$('#adb-status').text('ADB: 未连接').removeClass('connected').addClass('disconnected');
		}
	}).fail(function () {
		$('#adb-status').text('ADB: 未连接').removeClass('connected').addClass('disconnected');
	});
}

// 页面加载完成后初始化触摸板
$(document).ready(function () {
	initTouchpad();
	// 定期检查 ADB 状态
	checkAdbStatus();
	setInterval(checkAdbStatus, 10000);
});
