import {Component, OnInit, ChangeDetectorRef, ViewChild, ElementRef, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {EstadisticasService} from '../../services/estadisticas.service';
import {Chart, registerables} from 'chart.js';

Chart.register(...registerables);

interface OrdenStats {
  id: string;
  fecha: string;
  precio: number;
  plato: { nombre: string, categoria: string };
  metodoPago?: string;
}

interface NotificacionStats {
  id: string;
  tipo: string;
  fecha: string;
  fechaEnCurso?: string;
  ordenId?: string;
  nombreItem?: string;
  categoriaItem?: string;
  camareroUid?: string;
  camareroNombre?: string;
}

interface CuentaStats {
  id: string;
  fechaCreacion: string;
  fechaPago?: string;
  metodoPago?: string;
  mesas: any[];
}

@Component({
  selector: 'app-estadisticas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './estadisticas.html',
  styleUrls: ['./estadisticas.css']
})
export class EstadisticasComponent implements OnInit, OnDestroy {
  @ViewChild('salesChart') salesChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('staffChart') staffChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('dishesChart') dishesChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('paymentsChart') paymentsChartCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('heatmapChart') heatmapChartCanvas!: ElementRef<HTMLCanvasElement>;

  rawStats: any = null;
  loading = true;
  loadingRecs = false;
  error = '';

  // Filtros Globales
  filtroActual = '7';
  categoriaActual = 'Todas';
  categorias: string[] = ['Todas'];
  fechaInicioPersonalizada = '';
  fechaFinPersonalizada = '';

  // Ordenación de Tablas (Drill-down)
  sortTopPlatos = 'revenue'; // 'revenue' | 'count' | 'avg'

  kpis: any = {
    ventas: {value: 0, trend: 0, positive: true, label: 'Ingresos Brutos'},
    ventasNeto: {value: 0, trend: 0, positive: true, label: 'Ingresos Netos'},
    iva: {value: 0, trend: 0, positive: true, label: 'IVA (10%)'},
    tickets: {value: 0, trend: 0, positive: true},
    ticketMedio: {value: 0, trend: 0, positive: true},
    turnover: {value: 0, trend: 0, positive: true},
    cocina: {value: 0, trend: 0, positive: true},
    servicio: {value: 0, trend: 0, positive: true}
  };

  // Staff Profitability Simulator Pro
  staffHourlyRate = 12;
  globalCommission = 0; // % comisión sobre ventas
  staffConfigs: { [uid: string]: { rate: number, hours: number } } = {};
  teamTotals = {revenue: 0, cost: 0, margin: 0, avgRoi: 0};
  simulatedStaffProfitability: any[] = [];

  insights: string[] = [];
  topPlatos: any[] = [];
  slowPlatos: any[] = [];
  groupedRecommendations: any[] = [];
  staffStats: any = {fastest: null, topSeller: null, busiest: null, list: []};

  private charts: { [key: string]: any } = {};

  constructor(private statsService: EstadisticasService, private cdr: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.statsService.getHistorico().subscribe({
      next: (data) => {
        if (!data) {
          this.error = 'No se recibieron datos del servidor.';
          this.loading = false;
          return;
        }
        this.rawStats = data;
        this.extraerCategorias(data.ordenes || []);
        this.procesarYMostrar();
      },
      error: (err) => {
        console.error('Error fetching stats:', err);
        this.error = 'Error cargando datos del servidor.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  ngOnDestroy() {
    Object.values(this.charts).forEach(c => {
      if (c && typeof c.destroy === 'function') c.destroy();
    });
  }

  extraerCategorias(ordenes: any[]) {
    const cats = new Set(ordenes.map(o => o.plato?.categoria).filter(Boolean));
    this.categorias = ['Todas', ...Array.from(cats).sort()];
  }

  cambiarFiltro(dias: string) {
    this.filtroActual = dias;
    this.procesarYMostrar();
  }

  cambiarCategoria(cat: string) {
    this.categoriaActual = cat;
    this.procesarYMostrar();
  }

  cambiarOrdenPlatos(criterio: string) {
    this.sortTopPlatos = criterio;
    this.actualizarListasDerivadas();
    this.cdr.detectChanges();
  }

  actualizarSimulacionStaff() {
    if (!this.staffStats.list || this.staffStats.list.length === 0) return;

    const daysInPeriod = parseInt(this.filtroActual) || 30;
    const defaultHours = 8 * daysInPeriod;

    let totalRev = 0, totalCost = 0;

    this.simulatedStaffProfitability = this.staffStats.list.map((s: any) => {
      if (!this.staffConfigs[s.uid]) {
        this.staffConfigs[s.uid] = {rate: this.staffHourlyRate, hours: defaultHours};
      }

      const config = this.staffConfigs[s.uid];
      const laborCost = config.hours * config.rate;
      const commissionCost = s.revenue * (this.globalCommission / 100);
      const totalCostItem = laborCost + commissionCost;
      const netProfit = s.revenue - totalCostItem;
      const roi = totalCostItem > 0 ? (netProfit / totalCostItem) * 100 : 0;

      // Desglose de Nómina (España)
      // Coste Total Empresa = Sueldo Bruto + SS Empresa (~31.5%)
      const sueldoBruto = laborCost / 1.315;
      const ssEmpresa = laborCost - sueldoBruto;

      // Del Sueldo Bruto, el trabajador paga:
      // SS Trabajador (~6.45%)
      // IRPF (estimado 12% para salarios medios de hostelería)
      const ssTrabajador = sueldoBruto * 0.0645;
      const irpf = sueldoBruto * 0.12;
      const sueldoNeto = sueldoBruto - ssTrabajador - irpf;

      totalRev += s.revenue;
      totalCost += totalCostItem;

      return {
        ...s,
        rate: config.rate,
        hours: config.hours,
        laborCost,
        commissionCost,
        totalCost: totalCostItem,
        netProfit,
        roi,
        payroll: {
          sueldoBruto,
          ssEmpresa,
          ssTrabajador,
          irpf,
          sueldoNeto
        }
      };
    }).sort((a: any, b: any) => b.netProfit - a.netProfit);

    this.teamTotals = {
      revenue: totalRev,
      cost: totalCost,
      margin: totalRev - totalCost,
      avgRoi: totalCost > 0 ? ((totalRev - totalCost) / totalCost) * 100 : 0
    };

    this.cdr.detectChanges();
  }

  ajustarStaffIndividual(uid: string, field: 'rate' | 'hours', value: any) {
    const numVal = Number(value);
    if (this.staffConfigs[uid]) {
      this.staffConfigs[uid][field] = numVal;
      this.actualizarSimulacionStaff();
    }
  }

  aplicarTarifaGlobal() {
    Object.keys(this.staffConfigs).forEach(uid => {
      this.staffConfigs[uid].rate = this.staffHourlyRate;
    });
    this.actualizarSimulacionStaff();
  }

  procesarYMostrar() {
    this.loading = true;
    this.cdr.detectChanges();

    setTimeout(() => {
      try {
        const stats = this.calcularEstadisticas(this.rawStats);
        this.generarInsights(stats);

        // Guardar lista completa para simulador
        this.staffStats.list = stats.staff;
        this.actualizarSimulacionStaff();

        this.loading = false;
        this.cdr.detectChanges();
        setTimeout(() => this.inicializarGraficos(stats), 0);
      } catch (e) {
        console.error('Error processing stats:', e);
        this.error = 'Error al procesar los datos estadísticos.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    }, 400);
  }

  private dishStatsMap: any = {};

  private calcularEstadisticas(data: {
    cuentas: CuentaStats[],
    ordenes: OrdenStats[],
    notificaciones: NotificacionStats[]
  }) {
    const cuentas = data.cuentas || [];
    const ordenes = data.ordenes || [];
    const notificaciones = data.notificaciones || [];

    const ahora = new Date();
    let fLimite: Date;
    let fPrevLimite: Date;
    let diasFiltro = 30;

    if (this.filtroActual === 'custom' && this.fechaInicioPersonalizada && this.fechaFinPersonalizada) {
      fLimite = new Date(this.fechaInicioPersonalizada);
      const fFin = new Date(this.fechaFinPersonalizada);
      diasFiltro = Math.ceil(Math.abs(fFin.getTime() - fLimite.getTime()) / (1000 * 60 * 60 * 24));
      fPrevLimite = new Date(fLimite.getTime() - diasFiltro * 24 * 60 * 60 * 1000);
    } else {
      diasFiltro = parseInt(this.filtroActual) || 30;
      fLimite = new Date(ahora.getTime() - diasFiltro * 24 * 60 * 60 * 1000);
      fPrevLimite = new Date(fLimite.getTime() - diasFiltro * 24 * 60 * 60 * 1000);
    }

    const filterFn = (item: any, limit: Date, endLimit: Date = ahora) => {
      const d = new Date(item.fecha || item.fechaCreacion);
      const categoryMatch = this.categoriaActual === 'Todas' ||
        (item.plato?.categoria === this.categoriaActual) ||
        (item.categoriaItem === this.categoriaActual);

      if (this.filtroActual === 'custom' && this.fechaInicioPersonalizada && this.fechaFinPersonalizada) {
        const fFin = new Date(this.fechaFinPersonalizada);
        fFin.setHours(23, 59, 59, 999);
        return d >= limit && d <= fFin && categoryMatch;
      }
      return d >= limit && d < endLimit && categoryMatch;
    };

    const currentOrdenes = ordenes.filter(o => filterFn(o, fLimite));
    const prevOrdenes = ordenes.filter(o => filterFn(o, fPrevLimite, fLimite));
    const currentCuentas = cuentas.filter(c => filterFn(c, fLimite));
    const prevCuentas = cuentas.filter(c => filterFn(c, fPrevLimite, fLimite));

    const curV = currentOrdenes.reduce((s, o) => s + (Number(o.precio) / 100 || 0), 0);
    const preV = prevOrdenes.reduce((s, o) => s + (Number(o.precio) / 100 || 0), 0);

    const curNeto = curV / 1.10;
    const preNeto = preV / 1.10;
    const curIva = curV - curNeto;
    const preIva = preV - preNeto;

    this.kpis.ventas = this.getTrend(curV, preV);
    this.kpis.ventasNeto = this.getTrend(curNeto, preNeto);
    this.kpis.iva = this.getTrend(curIva, preIva);
    this.kpis.tickets = this.getTrend(currentCuentas.length, prevCuentas.length);

    const curTM = currentCuentas.length > 0 ? curV / currentCuentas.length : 0;
    const preTM = prevCuentas.length > 0 ? preV / prevCuentas.length : 0;
    this.kpis.ticketMedio = this.getTrend(curTM, preTM);

    const staffMap: any = {};
    this.dishStatsMap = {};
    const salesByDay: any = {};
    const hourlyActivity: number[] = new Array(24).fill(0);
    const heatmapData: any[] = []; // Para heatmap 7x24
    const categoryRevenue: any = {};

    // Inicializar heatmap 7x24
    for (let i = 0; i < 7; i++) {
      for (let j = 0; j < 24; j++) {
        heatmapData.push({x: j, y: i, v: 0});
      }
    }

    let tCocina = 0, cCocina = 0, tServicio = 0, cServicio = 0, tTurnover = 0, cTurnover = 0;
    const ordMap = new Map(ordenes.map(o => [o.id, o]));

    notificaciones.forEach(n => {
      if (!filterFn(n, fLimite)) return;

      if (n.tipo === 'Recoger' && n.ordenId) {
        const o = ordMap.get(n.ordenId);
        if (o) {
          const diff = Math.max(0, (new Date(n.fecha).getTime() - new Date(o.fecha).getTime()) / 60000);
          tCocina += diff;
          cCocina++;
          const p = n.nombreItem || 'Desconocido';
          if (!this.dishStatsMap[p]) this.dishStatsMap[p] = {count: 0, time: 0, revenue: 0};
          this.dishStatsMap[p].count++;
          this.dishStatsMap[p].time += diff;
          this.dishStatsMap[p].revenue += (Number(o.precio) / 100 || 0);

          const dateObj = new Date(o.fecha);
          const hour = dateObj.getHours();
          const day = (dateObj.getDay() + 6) % 7; // Lunes=0, Domingo=6

          hourlyActivity[hour] += (Number(o.precio) / 100 || 0);
          const hmIdx = day * 24 + hour;
          if (heatmapData[hmIdx]) heatmapData[hmIdx].v += 1;

          const cat = n.categoriaItem || 'Otros';
          categoryRevenue[cat] = (categoryRevenue[cat] || 0) + (Number(o.precio) / 100 || 0);
        }
      }

      if (n.fechaEnCurso && n.fecha && n.camareroUid) {
        const diff = Math.max(0, (new Date(n.fechaEnCurso).getTime() - new Date(n.fecha).getTime()) / 60000);
        tServicio += diff;
        cServicio++;
        if (!staffMap[n.camareroUid]) staffMap[n.camareroUid] = {
          nombre: n.camareroNombre,
          count: 0,
          time: 0,
          revenue: 0,
          accuracy: 0
        };
        staffMap[n.camareroUid].count++;
        staffMap[n.camareroUid].time += diff;
        const o = ordMap.get(n.ordenId || '');
        if (o) staffMap[n.camareroUid].revenue += (Number(o.precio) / 100 || 0);
      }
    });

    currentCuentas.forEach(c => {
      if (c.fechaPago) {
        const diff = Math.max(0, (new Date(c.fechaPago).getTime() - new Date(c.fechaCreacion).getTime()) / 60000);
        tTurnover += diff;
        cTurnover++;
      }
    });

    this.kpis.cocina = {value: cCocina > 0 ? tCocina / cCocina : 0};
    this.kpis.servicio = {value: cServicio > 0 ? tServicio / cServicio : 0};
    this.kpis.turnover = {value: cTurnover > 0 ? tTurnover / cTurnover : 0};

    currentOrdenes.forEach(o => {
      const d = new Date(o.fecha).toLocaleDateString();
      salesByDay[d] = (salesByDay[d] || 0) + (Number(o.precio) / 100 || 0);
    });

    this.actualizarListasDerivadas();

    return {
      salesByDay,
      staff: Object.values(staffMap).map((s: any) => ({
        ...s,
        avgSpeed: s.count > 0 ? s.time / s.count : 0,
        avgTicket: s.count > 0 ? s.revenue / s.count : 0
      })),
      dishes: Object.entries(this.dishStatsMap).map(([name, s]: any) => {
        // Simulamos un margen de beneficio (entre 40% y 70%) para que la matriz tenga sentido real
        const simulatedMarginFactor = 0.4 + (Math.abs(name.length % 10) / 33);
        return {
          name, ...s,
          avg: s.count > 0 ? s.time / s.count : 0,
          profitability: (s.revenue / s.count) * simulatedMarginFactor
        };
      }),
      payments: {
        efectivo: currentOrdenes.filter(o => o.metodoPago === 'EFECTIVO').reduce((s, o) => s + (Number(o.precio) / 100 || 0), 0),
        tarjeta: currentOrdenes.filter(o => o.metodoPago !== 'EFECTIVO').reduce((s, o) => s + (Number(o.precio) / 100 || 0), 0)
      },
      hourlyActivity,
      heatmapData,
      categoryRevenue
    };
  }

  private actualizarListasDerivadas() {
    const all = Object.entries(this.dishStatsMap).map(([name, s]: any) => ({
      name,
      count: s.count,
      revenue: s.revenue,
      avg: s.count > 0 ? s.time / s.count : 0
    }));

    this.topPlatos = [...all].sort((a, b) => {
      if (this.sortTopPlatos === 'revenue') return b.revenue - a.revenue;
      if (this.sortTopPlatos === 'count') return b.count - a.count;
      return b.avg - a.avg;
    }).slice(0, 5);

    this.slowPlatos = [...all].sort((a, b) => b.avg - a.avg).slice(0, 5);
  }

  private getTrend(current: number, prev: number, inverse = false) {
    const diff = prev > 0 ? ((current - prev) / prev) * 100 : 0;
    return {value: current, trend: diff, positive: inverse ? diff < 0 : diff > 0};
  }

  private generarInsights(stats: any) {
    this.insights = [];
    if (this.kpis.ventas.trend > 10) this.insights.push(`Impulso Financiero: Ingresos +${this.kpis.ventas.trend.toFixed(1)}% vs periodo anterior.`);
    if (this.kpis.ticketMedio.trend > 5) this.insights.push(`Optimización de Menú: El ticket medio ha crecido un ${this.kpis.ticketMedio.trend.toFixed(1)}%.`);

    const peakHour = stats.hourlyActivity.indexOf(Math.max(...stats.hourlyActivity, 0));
    if (Math.max(...stats.hourlyActivity) > 0) {
      this.insights.push(`Pico de Demanda: Las ${peakHour}:00h concentran el mayor volumen de ventas.`);
    }

    const slow = this.slowPlatos[0];
    if (slow?.avg > 18) {
      this.insights.push(`Alerta de Cocina: '${slow.name}' promedia ${slow.avg.toFixed(1)} min. Considera revisar su preparación.`);
    }

    const topStaff = stats.staff.sort((a: any, b: any) => b.revenue - a.revenue)[0];
    if (topStaff) this.insights.push(`MVP Staff: ${topStaff.nombre} lidera la facturación del equipo.`);
  }

  private inicializarGraficos(stats: any) {
    Object.values(this.charts).forEach(c => {
      if (c) c.destroy();
    });
    this.charts = {};

    const createChart = (canvas: ElementRef, config: any) => {
      if (!canvas || !canvas.nativeElement) return null;
      return new Chart(canvas.nativeElement, config);
    };

    // Calcular Medias para Cuadrantes de Ingeniería de Menú
    const avgPop = stats.dishes.length > 0 ? stats.dishes.reduce((s: number, d: any) => s + d.count, 0) / stats.dishes.length : 0;
    const avgProf = stats.dishes.length > 0 ? stats.dishes.reduce((s: number, d: any) => s + d.profitability, 0) / stats.dishes.length : 0;

    this.generarRecomendacionesEstrategicas(stats.dishes, avgPop, avgProf);

    // Identificar Top Performers de Staff
    if (stats.staff.length > 0) {
      this.staffStats.fastest = [...stats.staff].sort((a, b) => a.avgSpeed - b.avgSpeed)[0];
      this.staffStats.topSeller = [...stats.staff].sort((a, b) => b.revenue - a.revenue)[0];
      this.staffStats.busiest = [...stats.staff].sort((a, b) => b.count - a.count)[0];
    }

    this.charts['sales'] = createChart(this.salesChartCanvas, {
      type: 'line',
      data: {
        labels: Object.keys(stats.salesByDay),
        datasets: [{
          label: 'Ventas (€)',
          data: Object.values(stats.salesByDay),
          borderColor: '#6366f1',
          backgroundColor: 'rgba(99, 102, 241, 0.1)',
          fill: true,
          tension: 0.4,
          pointRadius: 3
        }]
      },
      options: {responsive: true, maintainAspectRatio: false, plugins: {legend: {display: false}}}
    });

    this.charts['staffEff'] = createChart(this.staffChartCanvas, {
      type: 'bar',
      data: {
        labels: stats.staff.map((s: any) => s.nombre),
        datasets: [
          {
            label: 'Volumen Pedidos',
            data: stats.staff.map((s: any) => s.count),
            backgroundColor: 'rgba(99, 102, 241, 0.5)',
            yAxisID: 'y'
          },
          {
            label: 'Velocidad Media (min)',
            data: stats.staff.map((s: any) => s.avgSpeed),
            borderColor: '#ef4444',
            backgroundColor: '#ef4444',
            type: 'line',
            yAxisID: 'y1',
            tension: 0.3
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {position: 'left', title: {display: true, text: 'Pedidos'}},
          y1: {position: 'right', grid: {display: false}, title: {display: true, text: 'Minutos'}}
        }
      }
    });

    if (stats.dishes.length > 0) {
      this.charts['scatter'] = createChart(this.dishesChartCanvas, {
        type: 'scatter',
        data: {
          datasets: [{
            label: 'Platos',
            data: stats.dishes.map((d: any) => ({x: d.count, y: d.profitability, label: d.name})),
            backgroundColor: stats.dishes.map((d: any) => {
              if (d.count >= avgPop && d.profitability >= avgProf) return '#10b981'; // Estrella
              if (d.count >= avgPop && d.profitability < avgProf) return '#6366f1'; // Caballo
              if (d.count < avgPop && d.profitability >= avgProf) return '#f59e0b'; // Puzzle
              return '#ef4444'; // Perro
            })
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            tooltip: {
              callbacks: {
                label: (ctx: any) => ctx.raw.label + ': ' + ctx.raw.x + ' pedidos, ' + ctx.raw.y.toFixed(2) + '€ margen'
              }
            }
          },
          scales: {
            x: {
              title: {display: true, text: 'Popularidad (Pedidos)'},
              grid: {color: (ctx: any) => ctx.tick.value === Math.round(avgPop) ? '#64748b' : '#e2e8f0'}
            },
            y: {
              title: {display: true, text: 'Rentabilidad (Margen €)'},
              grid: {color: (ctx: any) => ctx.tick.value === Math.round(avgProf) ? '#64748b' : '#e2e8f0'}
            }
          }
        }
      });
    }

    if (stats.heatmapData.length > 0) {
      this.charts['heatmap'] = createChart(this.heatmapChartCanvas, {
        type: 'bar',
        data: {
          labels: Array.from({length: 24}, (_, i) => `${i}h`),
          datasets: ['Lun', 'Mar', 'Mie', 'Jue', 'Vie', 'Sab', 'Dom'].map((day, i) => ({
            label: day,
            data: stats.heatmapData.filter((d: any) => d.y === i).map((d: any) => d.v),
            backgroundColor: `rgba(99, 102, 241, ${0.2 + (i * 0.1)})`
          }))
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {x: {stacked: true}, y: {stacked: true}}
        }
      });
    }

    this.charts['payments'] = createChart(this.paymentsChartCanvas, {
      type: 'doughnut',
      data: {
        labels: ['Efectivo', 'Tarjeta'],
        datasets: [{
          data: [stats.payments.efectivo, stats.payments.tarjeta],
          backgroundColor: ['#10b981', '#6366f1'],
          borderWidth: 0
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '75%',
        plugins: {
          legend: {position: 'bottom'}
        }
      }
    });
  }

  private generarRecomendacionesEstrategicas(dishes: any[], avgPop: number, avgProf: number) {
    this.loadingRecs = true;
    this.groupedRecommendations = [];
    this.cdr.detectChanges();

    setTimeout(() => {
      const groups: {
        [key: string]: { type: string, action: string, dishes: string[], color: string, priority: number }
      } = {
        'ESTRELLA': {
          type: 'ESTRELLA',
          action: 'Mantener calidad y ubicación privilegiada en carta.',
          dishes: [],
          color: 'estrella',
          priority: 4
        },
        'CABALLO': {
          type: 'CABALLO',
          action: 'Aumentar precio ligeramente o reducir tamaño de porción.',
          dishes: [],
          color: 'caballo',
          priority: 3
        },
        'PUZZLE': {
          type: 'PUZZLE',
          action: 'Promocionar mediante sugerencias o técnicas de Upselling.',
          dishes: [],
          color: 'puzzle',
          priority: 2
        },
        'PERRO': {
          type: 'PERRO',
          action: 'Considerar eliminación o rediseño total de la receta.',
          dishes: [],
          color: 'perro',
          priority: 1
        }
      };

      dishes.forEach(d => {
        let type = 'PERRO';
        if (d.count >= avgPop && d.profitability >= avgProf) type = 'ESTRELLA';
        else if (d.count >= avgPop && d.profitability < avgProf) type = 'CABALLO';
        else if (d.count < avgPop && d.profitability >= avgProf) type = 'PUZZLE';

        groups[type].dishes.push(d.name);
      });

      this.groupedRecommendations = Object.values(groups)
        .filter(g => g.dishes.length > 0)
        .sort((a, b) => b.priority - a.priority);

      this.loadingRecs = false;
      this.cdr.detectChanges();
    }, 1500); // Simulamos procesamiento pesado para el plan estratégico
  }
}
