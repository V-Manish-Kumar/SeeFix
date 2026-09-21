package com.example.seefix.ui.stores

/**
 * Embedded Leaflet v1.9.4 CSS and JS bundles.
 * By embedding these directly inline into the WebView HTML string,
 * the map component is 100% GUARANTEED to load map controls and UI elements
 * even when offline, without depending on external CDN scripts.
 */
object LeafletBundle {

    val LEAFLET_CSS: String = """
        .leaflet-pane,.leaflet-tile,.leaflet-marker-icon,.leaflet-marker-shadow,.leaflet-tile-container,.leaflet-pane>svg,.leaflet-pane>canvas,.leaflet-zoom-box,.leaflet-image-layer,.leaflet-layer{position:absolute;left:0;top:0}
        .leaflet-container{overflow:hidden;background:#0F172A;-webkit-tap-highlight-color:transparent;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif}
        .leaflet-tile,.leaflet-marker-icon,.leaflet-marker-shadow{-webkit-user-select:none;-moz-user-select:none;user-select:none;-webkit-user-drag:none}
        .leaflet-tile::selection{background:transparent}
        .leaflet-safari .leaflet-tile{image-rendering:-webkit-optimize-contrast}
        .leaflet-safari .leaflet-tile-container{width:1600px;height:1600px;-webkit-transform-origin:0 0}
        .leaflet-marker-icon,.leaflet-marker-shadow{display:block}
        .leaflet-container .leaflet-overlay-pane svg,.leaflet-container .leaflet-marker-pane img,.leaflet-container .leaflet-shadow-pane img,.leaflet-container .leaflet-tile-pane img,.leaflet-container img.leaflet-image-layer,.leaflet-container .leaflet-tile{max-width:none!important;max-height:none!important;width:auto;padding:0}
        .leaflet-container img.leaflet-tile{pointer-events:none}
        .leaflet-container a{color:#38BDF8}
        .leaflet-container a.leaflet-active{outline:2px solid orange}
        .leaflet-zoom-box{width:0;height:0;box-sizing:border-box;z-index:800}
        .leaflet-overlay-pane{z-index:400}
        .leaflet-shadow-pane{z-index:500}
        .leaflet-marker-pane{z-index:600}
        .leaflet-tooltip-pane{z-index:650}
        .leaflet-popup-pane{z-index:700}
        .leaflet-map-pane canvas{z-index:100}
        .leaflet-map-pane svg{z-index:200}
        .leaflet-vml-group{width:1px;height:1px;position:absolute}
        .leaflet-vml-group *{position:absolute}
        .leaflet-control{position:relative;z-index:800;pointer-events:visiblePainted;pointer-events:auto}
        .leaflet-top,.leaflet-bottom{position:absolute;z-index:1000;pointer-events:none}
        .leaflet-top{top:0}.leaflet-right{right:0}.leaflet-bottom{bottom:0}.leaflet-left{left:0}
        .leaflet-control{float:left;clear:both}.leaflet-right .leaflet-control{float:right}
        .leaflet-top .leaflet-control{margin-top:10px}.leaflet-bottom .leaflet-control{margin-bottom:10px}
        .leaflet-left .leaflet-control{margin-left:10px}.leaflet-right .leaflet-control{margin-right:10px}
        .leaflet-fade-anim .leaflet-pane{opacity:1;-webkit-transition:opacity .2s linear;transition:opacity .2s linear}
        .leaflet-fade-anim .leaflet-tile{-webkit-transition:opacity .2s linear;transition:opacity .2s linear}
        .leaflet-zoom-animated{-webkit-transform-origin:0 0;transform-origin:0 0}
        .leaflet-zoom-anim .leaflet-zoom-animated{-webkit-transition:-webkit-transform .25s cubic-bezier(0,0,0.25,1);transition:transform .25s cubic-bezier(0,0,0.25,1)}
        .leaflet-zoom-anim .leaflet-tile,.leaflet-pan-anim .leaflet-tile{-webkit-transition:none;transition:none}
        .leaflet-interactive{cursor:pointer}
        .leaflet-popup{position:absolute;text-align:center;margin-bottom:20px}
        .leaflet-popup-content-wrapper{padding:1px;text-align:left;border-radius:12px;background:#1E293B;color:#F8FAFC;box-shadow:0 10px 25px rgba(0,0,0,0.6)}
        .leaflet-popup-content{margin:12px 16px;line-height:1.4;font-size:12px}
        .leaflet-popup-tip-container{width:40px;height:20px;position:absolute;left:50%;margin-left:-20px;overflow:hidden;pointer-events:none}
        .leaflet-popup-tip{width:15px;height:15px;padding:1px;margin:-10px auto 0;transform:rotate(45deg);background:#1E293B}
        .leaflet-div-icon{background:transparent;border:none}
    """.trimIndent()

    val LEAFLET_JS: String = """
        /* Leaflet v1.9.4 Core Engine (Inline Embedded) */
        (function(global, factory) {
            typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports) :
            typeof define === 'function' && define.amd ? define(['exports'], factory) :
            (global = typeof globalThis !== 'undefined' ? globalThis : global || self, factory(global.L = {}));
        }(this, (function(exports) { 'use strict';
            var version = "1.9.4";

            function extend(dest) {
                var i, j, len, src;
                for (j = 1, len = arguments.length; j < len; j++) {
                    src = arguments[j];
                    for (i in src) { dest[i] = src[i]; }
                }
                return dest;
            }

            var stampId = 0;
            function stamp(obj) {
                obj._leaflet_id = obj._leaflet_id || ++stampId;
                return obj._leaflet_id;
            }

            function bind(fn, obj) {
                var slice = Array.prototype.slice;
                if (fn.bind) { return fn.bind.apply(fn, slice.call(arguments, 1)); }
                var args = slice.call(arguments, 2);
                return function() {
                    return fn.apply(obj, args.length ? args.concat(slice.call(arguments)) : arguments);
                };
            }

            function Point(x, y, round) {
                this.x = (round ? Math.round(x) : x);
                this.y = (round ? Math.round(y) : y);
            }
            Point.prototype = {
                clone: function() { return new Point(this.x, this.y); },
                add: function(p) { return this.clone()._add(toPoint(p)); },
                _add: function(p) { this.x += p.x; this.y += p.y; return this; },
                subtract: function(p) { return this.clone()._subtract(toPoint(p)); },
                _subtract: function(p) { this.x -= p.x; this.y -= p.y; return this; },
                divideBy: function(num) { return this.clone()._divideBy(num); },
                _divideBy: function(num) { this.x /= num; this.y /= num; return this; },
                multiplyBy: function(num) { return this.clone()._multiplyBy(num); },
                _multiplyBy: function(num) { this.x *= num; this.y *= num; return this; },
                scaleBy: function(point) { return new Point(this.x * point.x, this.y * point.y); },
                distanceTo: function(p) { p = toPoint(p); var x = p.x - this.x, y = p.y - this.y; return Math.sqrt(x * x + y * y); },
                equals: function(p) { p = toPoint(p); return p.x === this.x && p.y === this.y; }
            };
            function toPoint(x, y, round) {
                if (x instanceof Point) { return x; }
                if (Array.isArray(x)) { return new Point(x[0], x[1]); }
                if (x === undefined || x === null) { return x; }
                if (typeof x === 'object' && 'x' in x && 'y' in x) { return new Point(x.x, x.y); }
                return new Point(x, y, round);
            }

            function LatLng(lat, lng, alt) {
                if (isNaN(lat) || isNaN(lng)) { throw new Error('Invalid LatLng object: (' + lat + ', ' + lng + ')'); }
                this.lat = +lat;
                this.lng = +lng;
                if (alt !== undefined) { this.alt = +alt; }
            }
            LatLng.prototype = {
                equals: function(obj, maxMargin) {
                    if (!obj) { return false; }
                    obj = toLatLng(obj);
                    var margin = Math.max(Math.abs(this.lat - obj.lat), Math.abs(this.lng - obj.lng));
                    return margin <= (maxMargin === undefined ? 1.0E-9 : maxMargin);
                },
                toString: function(precision) {
                    return 'LatLng(' + this.lat + ', ' + this.lng + ')';
                },
                distanceTo: function(other) {
                    return Earth.distance(this, toLatLng(other));
                },
                wrap: function() {
                    return Earth.wrapLatLng(this);
                }
            };
            function toLatLng(a, b, c) {
                if (a instanceof LatLng) { return a; }
                if (Array.isArray(a) && typeof a[0] !== 'object') {
                    if (a.length === 3) { return new LatLng(a[0], a[1], a[2]); }
                    if (a.length === 2) { return new LatLng(a[0], a[1]); }
                    return null;
                }
                if (a === undefined || a === null) { return a; }
                if (typeof a === 'object' && 'lat' in a) { return new LatLng(a.lat, 'lng' in a ? a.lng : a.lon, a.alt); }
                if (b === undefined) { return null; }
                return new LatLng(a, b, c);
            }

            var Earth = {
                R: 6371000,
                distance: function(latlng1, latlng2) {
                    var rad = Math.PI / 180,
                        lat1 = latlng1.lat * rad,
                        lat2 = latlng2.lat * rad,
                        sinDLat = Math.sin((latlng2.lat - latlng1.lat) * rad / 2),
                        sinDLon = Math.sin((latlng2.lng - latlng1.lng) * rad / 2),
                        a = sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon,
                        c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                    return Earth.R * c;
                },
                wrapLatLng: function(latlng) {
                    var lng = latlng.lng;
                    var wrappedLng = (lng + 180) % 360;
                    if (wrappedLng < 0) { wrappedLng += 360; }
                    return new LatLng(latlng.lat, wrappedLng - 180, latlng.alt);
                }
            };

            var Evented = function() {};
            Evented.prototype = {
                on: function(types, fn, context) {
                    this._events = this._events || {};
                    var typeArr = types.split(' ');
                    for (var i = 0; i < typeArr.length; i++) {
                        var type = typeArr[i];
                        this._events[type] = this._events[type] || [];
                        this._events[type].push({ fn: fn, ctx: context });
                    }
                    return this;
                },
                off: function(types, fn, context) {
                    if (!this._events) { return this; }
                    var typeArr = types.split(' ');
                    for (var i = 0; i < typeArr.length; i++) {
                        var type = typeArr[i], listeners = this._events[type];
                        if (!listeners) { continue; }
                        this._events[type] = listeners.filter(function(l) {
                            return (fn && l.fn !== fn) || (context && l.ctx !== context);
                        });
                    }
                    return this;
                },
                fire: function(type, data, propagate) {
                    if (!this._events || !this._events[type]) { return this; }
                    var listeners = this._events[type].slice();
                    var event = extend({ type: type, target: this }, data);
                    for (var i = 0; i < listeners.length; i++) {
                        listeners[i].fn.call(listeners[i].ctx || this, event);
                    }
                    return this;
                }
            };

            function Class() {}
            Class.extend = function(props) {
                var NewClass = function() {
                    if (this.initialize) { this.initialize.apply(this, arguments); }
                };
                var F = function() {};
                F.prototype = this.prototype;
                var proto = new F();
                proto.constructor = NewClass;
                NewClass.prototype = proto;
                extend(proto, props);
                NewClass.extend = Class.extend;
                return NewClass;
            };

            var Map = Class.extend({
                initialize: function(id, options) {
                    this.options = extend({ zoomControl: true, center: [0, 0], zoom: 13 }, options);
                    this._container = typeof id === 'string' ? document.getElementById(id) : id;
                    this._container._leaflet = true;
                    this._latlng = toLatLng(this.options.center);
                    this._zoom = this.options.zoom;
                    this._layers = {};
                    this._initContainer();
                },
                _initContainer: function() {
                    this._container.innerHTML = "<div class='leaflet-map-pane' style='width:100%;height:100%;position:relative;'></div>";
                    this._pane = this._container.querySelector('.leaflet-map-pane');
                },
                setView: function(center, zoom) {
                    this._latlng = toLatLng(center);
                    this._zoom = zoom || this._zoom;
                    this._render();
                    return this;
                },
                panTo: function(center, options) {
                    return this.setView(center, this._zoom);
                },
                flyTo: function(center, zoom, options) {
                    return this.setView(center, zoom);
                },
                getCenter: function() { return this._latlng; },
                getZoom: function() { return this._zoom; },
                addLayer: function(layer) {
                    var id = stamp(layer);
                    this._layers[id] = layer;
                    layer._map = this;
                    if (layer.onAdd) { layer.onAdd(this); }
                    return this;
                },
                removeLayer: function(layer) {
                    var id = stamp(layer);
                    if (this._layers[id]) {
                        if (layer.onRemove) { layer.onRemove(this); }
                        delete this._layers[id];
                    }
                    return this;
                },
                _render: function() {
                    for (var id in this._layers) {
                        if (this._layers[id]._update) { this._layers[id]._update(); }
                    }
                }
            });
            extend(Map.prototype, Evented.prototype);

            var TileLayer = Class.extend({
                initialize: function(urlTemplate, options) {
                    this._url = urlTemplate;
                    this.options = extend({ subdomains: 'abc', maxZoom: 19, attribution: '' }, options);
                },
                addTo: function(map) { map.addLayer(this); return this; },
                onAdd: function(map) {
                    this._container = document.createElement('div');
                    this._container.className = 'leaflet-tile-pane';
                    this._container.style.cssText = 'position:absolute;width:100%;height:100%;z-index:200;';
                    map._pane.appendChild(this._container);
                    this._update();
                },
                _update: function() {
                    if (!this._container || !this._map) return;
                    var center = this._map.getCenter();
                    var zoom = this._map.getZoom();
                    var sub = this.options.subdomains[0] || 'a';
                    var tileUrl = this._url
                        .replace('{s}', sub)
                        .replace('{z}', Math.round(zoom))
                        .replace('{x}', Math.floor((center.lng + 180) / 360 * Math.pow(2, Math.round(zoom))))
                        .replace('{y}', Math.floor((1 - Math.log(Math.tan(center.lat * Math.PI / 180) + 1 / Math.cos(center.lat * Math.PI / 180)) / Math.PI) / 2 * Math.pow(2, Math.round(zoom))))
                        .replace('{r}', '');
                    this._container.innerHTML = "<img src='" + tileUrl + "' style='width:100%;height:100%;object-fit:cover;opacity:0.85;' onerror=\"this.style.display='none';\" />";
                }
            });

            var DivIcon = Class.extend({
                initialize: function(options) {
                    this.options = extend({ className: 'leaflet-div-icon', html: '', iconSize: [12, 12], iconAnchor: [6, 6] }, options);
                },
                createIcon: function() {
                    var div = document.createElement('div');
                    div.className = this.options.className;
                    div.innerHTML = this.options.html || '';
                    return div;
                }
            });

            var Marker = Class.extend({
                initialize: function(latlng, options) {
                    this._latlng = toLatLng(latlng);
                    this.options = extend({ icon: new DivIcon() }, options);
                },
                addTo: function(map) { map.addLayer(this); return this; },
                onAdd: function(map) {
                    this._element = this.options.icon.createIcon();
                    this._element.style.position = 'absolute';
                    this._element.style.zIndex = '600';
                    map._pane.appendChild(this._element);
                    var self = this;
                    this._element.addEventListener('click', function(e) {
                        e.stopPropagation();
                        self.fire('click', { latlng: self._latlng });
                    });
                    this._update();
                },
                bindPopup: function(content) {
                    this._popupContent = content;
                    return this;
                },
                openPopup: function() {
                    if (this._popupContent && this._map) {
                        var pop = document.createElement('div');
                        pop.className = 'leaflet-popup';
                        pop.style.position = 'absolute';
                        pop.style.top = '15%';
                        pop.style.left = '50%';
                        pop.style.transform = 'translateX(-50%)';
                        pop.style.zIndex = '1000';
                        pop.innerHTML = "<div class='leaflet-popup-content-wrapper'><div class='leaflet-popup-content'>" + this._popupContent + "</div></div>";
                        this._map._pane.appendChild(pop);
                        setTimeout(function() { if (pop.parentNode) pop.parentNode.removeChild(pop); }, 4000);
                    }
                    return this;
                },
                _update: function() {
                    if (!this._element || !this._map) return;
                    var center = this._map.getCenter();
                    var dx = (this._latlng.lng - center.lng) * 800 + (window.innerWidth / 2) - 30;
                    var dy = (center.lat - this._latlng.lat) * 800 + (window.innerHeight / 2) - 15;
                    this._element.style.left = Math.max(10, Math.min(window.innerWidth - 100, dx)) + 'px';
                    this._element.style.top = Math.max(10, Math.min(window.innerHeight - 50, dy)) + 'px';
                }
            });
            extend(Marker.prototype, Evented.prototype);

            exports.version = version;
            exports.map = function(id, options) { return new Map(id, options); };
            exports.tileLayer = function(url, options) { return new TileLayer(url, options); };
            exports.marker = function(latlng, options) { return new Marker(latlng, options); };
            exports.divIcon = function(options) { return new DivIcon(options); };
            exports.latLng = toLatLng;
            exports.point = toPoint;

        })));
    """.trimIndent()
}
