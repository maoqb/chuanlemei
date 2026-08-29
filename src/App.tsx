import {
  Archive,
  BarChart3,
  CalendarDays,
  Camera,
  CheckCircle2,
  CircleAlert,
  Database,
  Download,
  Footprints,
  History,
  ImageIcon,
  Layers,
  ListFilter,
  PackagePlus,
  RefreshCw,
  Shirt,
  Sparkles,
  Trash2,
  Upload,
  Wand2,
} from 'lucide-react'
import { type ChangeEvent, type FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import './App.css'
import {
  formatReadableDate,
  formatShortDate,
  getDateRangeForPreset,
  isDateInputToday,
  toDateInputValue,
} from './domain/dates'
import { buildDashboardStats, getGarmentWearCount, getRecordsForGarment } from './domain/stats'
import {
  categoryAccent,
  categoryLabels,
  categoryOrder,
  type AppExport,
  type Garment,
  type GarmentCategory,
  type GarmentMap,
  type ImageSignature,
  type PeriodPreset,
  type RecognitionSlot,
  type WearRecord,
} from './domain/types'
import { createImageSignature, fileToDataUrl, recognizeGarments, resizeImageDataUrl } from './domain/vision'
import {
  deleteWearRecord,
  exportAppData,
  importAppData,
  loadAppData,
  saveGarment,
  saveWearRecord,
} from './storage/db'

type ViewKey = 'record' | 'wardrobe' | 'outfits' | 'stats'
type BusyState = 'idle' | 'camera' | 'image' | 'save' | 'import'

interface GarmentDraft {
  name: string
  category: GarmentCategory
  color: string
  brand: string
  note: string
  imageDataUrl?: string
  signature?: ImageSignature
}

const emptyDraft: GarmentDraft = {
  name: '',
  category: 'top',
  color: categoryAccent.top,
  brand: '',
  note: '',
}

const viewTabs: Array<{ key: ViewKey; label: string; icon: typeof Camera }> = [
  { key: 'record', label: '记录', icon: Camera },
  { key: 'wardrobe', label: '衣橱', icon: Shirt },
  { key: 'outfits', label: '组合', icon: Layers },
  { key: 'stats', label: '统计', icon: BarChart3 },
]

const periodPresets: Array<{ key: PeriodPreset; label: string }> = [
  { key: '7d', label: '7天' },
  { key: '30d', label: '30天' },
  { key: '90d', label: '90天' },
  { key: 'year', label: '今年' },
  { key: 'all', label: '全部' },
  { key: 'custom', label: '自定义' },
]

function App() {
  const [view, setView] = useState<ViewKey>('record')
  const [garments, setGarments] = useState<Garment[]>([])
  const [wearRecords, setWearRecords] = useState<WearRecord[]>([])
  const [busy, setBusy] = useState<BusyState>('idle')
  const [statusMessage, setStatusMessage] = useState('正在读取本地数据')
  const [draft, setDraft] = useState<GarmentDraft>(emptyDraft)
  const [wardrobeFilter, setWardrobeFilter] = useState<GarmentCategory | 'all'>('all')
  const [selectedGarmentId, setSelectedGarmentId] = useState<string>()
  const [periodPreset, setPeriodPreset] = useState<PeriodPreset>('30d')
  const [customRange, setCustomRange] = useState(() => ({
    start: getDateRangeForPreset('30d').start,
    end: toDateInputValue(),
  }))
  const [comboSelection, setComboSelection] = useState<GarmentMap>({})
  const [wearDate, setWearDate] = useState(toDateInputValue())
  const [capturedPhoto, setCapturedPhoto] = useState<string>()
  const [recognition, setRecognition] = useState<RecognitionSlot[]>([])
  const [captureNote, setCaptureNote] = useState('')
  const [cameraActive, setCameraActive] = useState(false)

  const videoRef = useRef<HTMLVideoElement | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)

  useEffect(() => {
    loadAppData()
      .then((data) => {
        setGarments(data.garments)
        setWearRecords(data.wearRecords)
        setSelectedGarmentId(data.garments[0]?.id)
        setStatusMessage(data.garments.length ? '本地数据已就绪' : '先导入衣物照片')
      })
      .catch((error) => setStatusMessage(getErrorMessage(error)))
  }, [])

  useEffect(
    () => () => {
      streamRef.current?.getTracks().forEach((track) => track.stop())
      streamRef.current = null
    },
    [],
  )

  const garmentsById = useMemo(() => new Map(garments.map((garment) => [garment.id, garment])), [garments])
  const activeGarments = useMemo(() => garments.filter((garment) => !garment.archivedAt), [garments])
  const effectiveComboSelection = useMemo(() => {
    const next = { ...comboSelection }
    for (const category of categoryOrder) {
      const stillExists = next[category] && activeGarments.some((garment) => garment.id === next[category])
      if (!stillExists) {
        next[category] = activeGarments.find((garment) => garment.category === category)?.id
      }
    }
    return next
  }, [activeGarments, comboSelection])
  const range = useMemo(
    () => getDateRangeForPreset(periodPreset, new Date(), customRange),
    [periodPreset, customRange],
  )
  const stats = useMemo(() => buildDashboardStats(garments, wearRecords, range), [garments, wearRecords, range])
  const filteredGarments = useMemo(
    () => activeGarments.filter((garment) => wardrobeFilter === 'all' || garment.category === wardrobeFilter),
    [activeGarments, wardrobeFilter],
  )
  const selectedGarment = selectedGarmentId
    ? garmentsById.get(selectedGarmentId)
    : stats.garmentStats[0]?.garment
  const selectedGarmentRecords = selectedGarment
    ? getRecordsForGarment(wearRecords, selectedGarment.id)
    : []
  const selectedCaptureMap = useMemo(
    () =>
      recognition.reduce<GarmentMap>((result, slot) => {
        if (slot.selectedGarmentId) {
          result[slot.category] = slot.selectedGarmentId
        }
        return result
      }, {}),
    [recognition],
  )
  const canSaveCapture =
    Boolean(capturedPhoto) && isDateInputToday(wearDate) && Object.values(selectedCaptureMap).some(Boolean)

  async function startCamera() {
    if (!navigator.mediaDevices?.getUserMedia) {
      setStatusMessage('当前浏览器没有开放相机能力')
      return
    }

    setBusy('camera')
    setStatusMessage('正在打开相机')
    try {
      stopCamera()
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1280 },
          height: { ideal: 960 },
        },
      })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        await videoRef.current.play()
      }
      setCameraActive(true)
      setStatusMessage('相机已打开')
    } catch (error) {
      setStatusMessage(getErrorMessage(error))
    } finally {
      setBusy('idle')
    }
  }

  function stopCamera() {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    setCameraActive(false)
  }

  async function captureFromCamera() {
    const video = videoRef.current
    const canvas = canvasRef.current
    if (!video || !canvas) {
      setStatusMessage('相机画面还没准备好')
      return
    }

    const width = video.videoWidth || 1280
    const height = video.videoHeight || 960
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) {
      setStatusMessage('无法截取相机画面')
      return
    }

    setBusy('image')
    setStatusMessage('正在识别衣物')
    try {
      context.drawImage(video, 0, 0, width, height)
      const rawPhoto = canvas.toDataURL('image/jpeg', 0.88)
      const photo = await resizeImageDataUrl(rawPhoto, 1280, 0.86)
      const signature = await createImageSignature(photo)
      const slots = recognizeGarments(signature, activeGarments)
      setCapturedPhoto(photo)
      setRecognition(slots)
      setWearDate(toDateInputValue())
      setStatusMessage('识别完成，确认后可保存')
    } catch (error) {
      setStatusMessage(getErrorMessage(error))
    } finally {
      setBusy('idle')
    }
  }

  async function handleGarmentPhoto(event: ChangeEvent<HTMLInputElement>) {
    const file = event.currentTarget.files?.[0]
    event.currentTarget.value = ''
    if (!file) {
      return
    }

    setBusy('image')
    setStatusMessage('正在导入衣物图片')
    try {
      const dataUrl = await fileToDataUrl(file)
      const resized = await resizeImageDataUrl(dataUrl, 900, 0.88)
      const signature = await createImageSignature(resized)
      setDraft((current) => ({ ...current, imageDataUrl: resized, signature }))
      setStatusMessage('衣物图片已导入')
    } catch (error) {
      setStatusMessage(getErrorMessage(error))
    } finally {
      setBusy('idle')
    }
  }

  async function handleSaveGarment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = draft.name.trim()
    if (!name) {
      setStatusMessage('衣物名称不能为空')
      return
    }

    const now = new Date().toISOString()
    const garment: Garment = {
      id: createId('garment'),
      name,
      category: draft.category,
      color: draft.color,
      brand: draft.brand.trim() || undefined,
      note: draft.note.trim() || undefined,
      imageDataUrl: draft.imageDataUrl,
      signature: draft.signature,
      createdAt: now,
      updatedAt: now,
    }

    setBusy('save')
    try {
      await saveGarment(garment)
      setGarments((current) => [garment, ...current])
      setDraft({ ...emptyDraft, color: categoryAccent[draft.category], category: draft.category })
      setSelectedGarmentId(garment.id)
      setStatusMessage(`${garment.name} 已加入衣橱`)
    } catch (error) {
      setStatusMessage(getErrorMessage(error))
    } finally {
      setBusy('idle')
    }
  }

  async function archiveGarment(garment: Garment) {
    const next = { ...garment, archivedAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
    await saveGarment(next)
    setGarments((current) => current.map((item) => (item.id === garment.id ? next : item)))
    setStatusMessage(`${garment.name} 已停用`)
  }

  function updateRecognitionSlot(category: GarmentCategory, selectedGarmentId: string) {
    setRecognition((current) =>
      categoryOrder.map((slotCategory) => {
        const existing = current.find((slot) => slot.category === slotCategory)
        const alternatives = existing?.alternatives ?? []
        if (slotCategory !== category) {
          return existing ?? { category: slotCategory, confidence: 0, alternatives: [] }
        }
        const candidate = alternatives.find((item) => item.garmentId === selectedGarmentId)
        return {
          category,
          selectedGarmentId: selectedGarmentId || undefined,
          confidence: candidate?.confidence ?? (selectedGarmentId ? 1 : 0),
          alternatives,
        }
      }),
    )
  }

  async function saveCaptureRecord() {
    if (!capturedPhoto) {
      setStatusMessage('请先拍照')
      return
    }
    if (!isDateInputToday(wearDate)) {
      setStatusMessage('记录日期必须是今天')
      return
    }
    if (!Object.values(selectedCaptureMap).some(Boolean)) {
      setStatusMessage('至少确认一件衣物')
      return
    }

    const record: WearRecord = {
      id: createId('wear'),
      wornAt: wearDate,
      capturedAt: new Date().toISOString(),
      evidencePhotoDataUrl: capturedPhoto,
      garmentIds: selectedCaptureMap,
      recognition,
      note: captureNote.trim() || undefined,
    }

    setBusy('save')
    try {
      await saveWearRecord(record)
      setWearRecords((current) => [record, ...current].sort((left, right) => right.wornAt.localeCompare(left.wornAt)))
      setCapturedPhoto(undefined)
      setRecognition([])
      setCaptureNote('')
      setStatusMessage('今日穿着已记录')
    } catch (error) {
      setStatusMessage(getErrorMessage(error))
    } finally {
      setBusy('idle')
    }
  }

  async function removeRecord(record: WearRecord) {
    await deleteWearRecord(record.id)
    setWearRecords((current) => current.filter((item) => item.id !== record.id))
    setStatusMessage(`${formatReadableDate(record.wornAt)} 的记录已删除`)
  }

  async function handleExportData() {
    const payload = await exportAppData()
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `chuanlemei-${toDateInputValue()}.json`
    anchor.click()
    URL.revokeObjectURL(url)
    setStatusMessage('数据已导出')
  }

  async function handleImportData(event: ChangeEvent<HTMLInputElement>) {
    const file = event.currentTarget.files?.[0]
    event.currentTarget.value = ''
    if (!file) {
      return
    }

    setBusy('import')
    try {
      const payload = JSON.parse(await file.text()) as AppExport
      await importAppData(payload)
      const data = await loadAppData()
      setGarments(data.garments)
      setWearRecords(data.wearRecords)
      setSelectedGarmentId(data.garments[0]?.id)
      setStatusMessage('数据已导入')
    } catch (error) {
      setStatusMessage(getErrorMessage(error))
    } finally {
      setBusy('idle')
    }
  }

  function renderContent() {
    if (view === 'wardrobe') {
      return (
        <section className="view-section">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Wardrobe</p>
              <h2>衣橱导入</h2>
            </div>
            <div className="segmented">
              {(['all', ...categoryOrder] as Array<GarmentCategory | 'all'>).map((category) => (
                <button
                  type="button"
                  className={wardrobeFilter === category ? 'is-active' : ''}
                  key={category}
                  onClick={() => setWardrobeFilter(category)}
                >
                  {category === 'all' ? '全部' : categoryLabels[category]}
                </button>
              ))}
            </div>
          </div>

          <div className="wardrobe-layout">
            <form className="garment-form" onSubmit={handleSaveGarment}>
              <div className="form-photo">
                {draft.imageDataUrl ? (
                  <img src={draft.imageDataUrl} alt="待导入衣物" />
                ) : (
                  <div className="photo-placeholder">
                    <ImageIcon aria-hidden="true" />
                    <span>衣物照片</span>
                  </div>
                )}
                <label className="button secondary">
                  <Upload aria-hidden="true" size={18} />
                  导入图片
                  <input type="file" accept="image/*" capture="environment" onChange={handleGarmentPhoto} />
                </label>
              </div>

              <div className="form-fields">
                <label>
                  名称
                  <input
                    value={draft.name}
                    onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
                    placeholder="黑色衬衫"
                  />
                </label>
                <label>
                  类型
                  <select
                    value={draft.category}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        category: event.target.value as GarmentCategory,
                        color: categoryAccent[event.target.value as GarmentCategory],
                      }))
                    }
                  >
                    {categoryOrder.map((category) => (
                      <option value={category} key={category}>
                        {categoryLabels[category]}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  主色
                  <span className="color-field">
                    <input
                      type="color"
                      value={draft.color}
                      onChange={(event) => setDraft((current) => ({ ...current, color: event.target.value }))}
                    />
                    <span>{draft.color}</span>
                  </span>
                </label>
                <label>
                  品牌
                  <input
                    value={draft.brand}
                    onChange={(event) => setDraft((current) => ({ ...current, brand: event.target.value }))}
                    placeholder="可选"
                  />
                </label>
                <label className="wide">
                  备注
                  <textarea
                    value={draft.note}
                    onChange={(event) => setDraft((current) => ({ ...current, note: event.target.value }))}
                    rows={3}
                  />
                </label>
                <button className="button primary" type="submit" disabled={busy !== 'idle'}>
                  <PackagePlus aria-hidden="true" size={18} />
                  加入衣橱
                </button>
              </div>
            </form>

            <div className="garment-grid">
              {filteredGarments.map((garment) => (
                <article
                  className={`garment-card ${selectedGarmentId === garment.id ? 'is-selected' : ''}`}
                  key={garment.id}
                >
                  <button type="button" className="garment-main" onClick={() => setSelectedGarmentId(garment.id)}>
                    <GarmentImage garment={garment} />
                    <span>
                      <strong>{garment.name}</strong>
                      <small>
                        {categoryLabels[garment.category]} · 穿过 {getGarmentWearCount(wearRecords, garment.id)} 次
                      </small>
                    </span>
                  </button>
                  <button
                    type="button"
                    className="icon-button"
                    aria-label={`停用 ${garment.name}`}
                    onClick={() => archiveGarment(garment)}
                  >
                    <Archive aria-hidden="true" size={17} />
                  </button>
                </article>
              ))}
              {!filteredGarments.length && <EmptyState icon={Shirt} title="还没有衣物" text="导入照片后才能识别穿着记录。" />}
            </div>
          </div>
        </section>
      )
    }

    if (view === 'outfits') {
      return (
        <section className="view-section">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Outfits</p>
              <h2>上衣、裤子和鞋组合</h2>
            </div>
          </div>

          <div className="composer-layout">
            <div className="outfit-preview" aria-label="搭配预览">
              {categoryOrder.map((category) => (
                <div className={`outfit-slot outfit-${category}`} key={category}>
                  <span>{categoryLabels[category]}</span>
                  {effectiveComboSelection[category] ? (
                    <GarmentImage garment={garmentsById.get(effectiveComboSelection[category]!)!} />
                  ) : (
                    <div className="slot-placeholder">
                      <CategoryIcon category={category} />
                    </div>
                  )}
                </div>
              ))}
            </div>

            <div className="composer-controls">
              {categoryOrder.map((category) => (
                <label key={category}>
                  {categoryLabels[category]}
                  <select
                    value={effectiveComboSelection[category] ?? ''}
                    onChange={(event) =>
                      setComboSelection((current) => ({ ...current, [category]: event.target.value || undefined }))
                    }
                  >
                    <option value="">未选择</option>
                    {activeGarments
                      .filter((garment) => garment.category === category)
                      .map((garment) => (
                        <option value={garment.id} key={garment.id}>
                          {garment.name}
                        </option>
                      ))}
                  </select>
                </label>
              ))}
              <div className="combo-summary">
                <Sparkles aria-hidden="true" />
                <span>{describeCombo(effectiveComboSelection, garmentsById)}</span>
              </div>
            </div>
          </div>

          <div className="list-block">
            <div className="block-heading">
              <h3>周期内常穿组合</h3>
              <span>
                {range.start} 至 {range.end}
              </span>
            </div>
            <div className="outfit-list">
              {stats.outfitStats.slice(0, 6).map((outfit) => (
                <article className="outfit-row" key={outfit.key}>
                  <div className="outfit-mini">
                    {outfit.garmentIds.map((id) => {
                      const garment = garmentsById.get(id)
                      return garment ? <GarmentImage garment={garment} key={id} /> : null
                    })}
                  </div>
                  <div>
                    <strong>
                      {outfit.garmentIds
                        .map((id) => garmentsById.get(id)?.name)
                        .filter(Boolean)
                        .join(' / ')}
                    </strong>
                    <small>
                      穿过 {outfit.count} 次 · 最近{' '}
                      {outfit.lastWornAt ? formatReadableDate(outfit.lastWornAt) : '-'}
                    </small>
                  </div>
                </article>
              ))}
              {!stats.outfitStats.length && <EmptyState icon={Layers} title="暂无组合记录" text="完成拍照记录后这里会自动统计。" />}
            </div>
          </div>
        </section>
      )
    }

    if (view === 'stats') {
      return (
        <section className="view-section">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Analytics</p>
              <h2>历史周期数据</h2>
            </div>
            <PeriodControl
              periodPreset={periodPreset}
              setPeriodPreset={setPeriodPreset}
              customRange={customRange}
              setCustomRange={setCustomRange}
            />
          </div>

          <div className="metric-grid">
            <MetricCard label="穿着记录" value={stats.totalRecords} detail={`${range.start} - ${range.end}`} />
            <MetricCard label="衣物计次" value={stats.totalGarmentWears} detail="上衣、裤子、鞋分别计数" />
            <MetricCard label="活跃衣物" value={stats.activeGarments} detail="未停用衣物" />
          </div>

          <div className="analytics-layout">
            <div className="chart-panel">
              <div className="block-heading">
                <h3>日历趋势</h3>
                <span>按穿搭记录数</span>
              </div>
              <DailyBars dailyCounts={stats.dailyCounts} />
            </div>
            <div className="chart-panel">
              <div className="block-heading">
                <h3>衣物排行</h3>
                <span>周期内计次</span>
              </div>
              <div className="rank-list">
                {stats.garmentStats.slice(0, 8).map((item) => (
                  <button
                    type="button"
                    className="rank-row"
                    key={item.garment.id}
                    onClick={() => setSelectedGarmentId(item.garment.id)}
                  >
                    <GarmentImage garment={item.garment} />
                    <span>
                      <strong>{item.garment.name}</strong>
                      <small>总计 {item.wearCount} 次</small>
                    </span>
                    <b>{item.rangeCount}</b>
                  </button>
                ))}
                {!stats.garmentStats.length && <EmptyState icon={ListFilter} title="暂无排行" text="记录后自动生成。" />}
              </div>
            </div>
          </div>
        </section>
      )
    }

    return (
      <section className="view-section">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Capture</p>
            <h2>今日拍照记录</h2>
          </div>
          <span className={isDateInputToday(wearDate) ? 'date-pill is-ok' : 'date-pill is-blocked'}>
            {isDateInputToday(wearDate) ? (
              <CheckCircle2 aria-hidden="true" size={16} />
            ) : (
              <CircleAlert aria-hidden="true" size={16} />
            )}
            {wearDate}
          </span>
        </div>

        <div className="capture-layout">
          <div className="camera-panel">
            <div className="camera-frame">
              {capturedPhoto ? (
                <img src={capturedPhoto} alt="本次穿着照片" />
              ) : (
                <video ref={videoRef} muted playsInline autoPlay />
              )}
              {!cameraActive && !capturedPhoto && (
                <div className="camera-empty">
                  <Camera aria-hidden="true" />
                  <span>相机未开启</span>
                </div>
              )}
            </div>
            <canvas ref={canvasRef} hidden />
            <div className="camera-actions">
              <button type="button" className="button secondary" onClick={startCamera} disabled={busy !== 'idle'}>
                <Camera aria-hidden="true" size={18} />
                打开相机
              </button>
              <button
                type="button"
                className="button primary"
                onClick={captureFromCamera}
                disabled={!cameraActive || busy !== 'idle'}
              >
                <Wand2 aria-hidden="true" size={18} />
                拍照识别
              </button>
              {capturedPhoto && (
                <button
                  type="button"
                  className="button ghost"
                  onClick={() => {
                    setCapturedPhoto(undefined)
                    setRecognition([])
                  }}
                >
                  <RefreshCw aria-hidden="true" size={18} />
                  重拍
                </button>
              )}
            </div>
          </div>

          <div className="recognition-panel">
            <label>
              记录日期
              <input type="date" value={wearDate} onChange={(event) => setWearDate(event.target.value)} />
            </label>
            {!isDateInputToday(wearDate) && (
              <div className="inline-alert">
                <CircleAlert aria-hidden="true" size={18} />
                <span>只能保存本地当天的拍照记录。</span>
              </div>
            )}
            <div className="recognition-slots">
              {categoryOrder.map((category) => {
                const slot = recognition.find((item) => item.category === category)
                return (
                  <div className="recognition-slot" key={category}>
                    <div className="slot-title">
                      <CategoryIcon category={category} />
                      <strong>{categoryLabels[category]}</strong>
                      <span>{slot ? `${Math.round(slot.confidence * 100)}%` : '--'}</span>
                    </div>
                    <select
                      value={slot?.selectedGarmentId ?? ''}
                      onChange={(event) => updateRecognitionSlot(category, event.target.value)}
                      disabled={!capturedPhoto}
                    >
                      <option value="">未识别</option>
                      {activeGarments
                        .filter((garment) => garment.category === category)
                        .map((garment) => (
                          <option value={garment.id} key={garment.id}>
                            {garment.name}
                          </option>
                        ))}
                    </select>
                    <div className="candidate-list">
                      {slot?.alternatives.map((candidate) => (
                        <button
                          type="button"
                          key={candidate.garmentId}
                          onClick={() => updateRecognitionSlot(category, candidate.garmentId)}
                        >
                          {candidate.garmentName} {Math.round(candidate.confidence * 100)}%
                        </button>
                      ))}
                    </div>
                  </div>
                )
              })}
            </div>
            <label>
              备注
              <textarea value={captureNote} rows={3} onChange={(event) => setCaptureNote(event.target.value)} />
            </label>
            <button
              type="button"
              className="button primary full-width"
              disabled={!canSaveCapture || busy !== 'idle'}
              onClick={saveCaptureRecord}
            >
              <CheckCircle2 aria-hidden="true" size={18} />
              保存今日穿着
            </button>
          </div>
        </div>
      </section>
    )
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand">
          <span className="brand-mark">穿</span>
          <div>
            <h1>穿了没</h1>
            <p>拍照识别衣物，记录每件穿了多少次。</p>
          </div>
        </div>
        <nav className="view-tabs" aria-label="主导航">
          {viewTabs.map((tab) => {
            const Icon = tab.icon
            return (
              <button
                type="button"
                className={view === tab.key ? 'is-active' : ''}
                key={tab.key}
                onClick={() => setView(tab.key)}
              >
                <Icon aria-hidden="true" size={18} />
                <span>{tab.label}</span>
              </button>
            )
          })}
        </nav>
      </header>

      <main className="app-main">
        <aside className="side-panel">
          <div className="status-card">
            <span className={`status-dot ${busy !== 'idle' ? 'is-busy' : ''}`} />
            <p>{statusMessage}</p>
          </div>

          <div className="quick-stats">
            <div>
              <span>衣物</span>
              <strong>{activeGarments.length}</strong>
            </div>
            <div>
              <span>记录</span>
              <strong>{wearRecords.length}</strong>
            </div>
            <div>
              <span>本期</span>
              <strong>{stats.totalRecords}</strong>
            </div>
          </div>

          <div className="data-actions">
            <button type="button" className="button ghost" onClick={handleExportData}>
              <Download aria-hidden="true" size={18} />
              导出数据
            </button>
            <label className="button ghost">
              <Database aria-hidden="true" size={18} />
              导入数据
              <input type="file" accept="application/json" onChange={handleImportData} />
            </label>
          </div>

          <SelectedGarmentPanel
            garment={selectedGarment}
            records={selectedGarmentRecords}
            wearCount={selectedGarment ? getGarmentWearCount(wearRecords, selectedGarment.id) : 0}
          />

          <RecentRecords records={wearRecords.slice(0, 5)} garmentsById={garmentsById} onDelete={removeRecord} />
        </aside>

        <div className="content-panel">{renderContent()}</div>
      </main>
    </div>
  )
}

function PeriodControl({
  periodPreset,
  setPeriodPreset,
  customRange,
  setCustomRange,
}: {
  periodPreset: PeriodPreset
  setPeriodPreset: (value: PeriodPreset) => void
  customRange: { start: string; end: string }
  setCustomRange: (value: { start: string; end: string }) => void
}) {
  return (
    <div className="period-control">
      <div className="segmented">
        {periodPresets.map((preset) => (
          <button
            type="button"
            className={periodPreset === preset.key ? 'is-active' : ''}
            key={preset.key}
            onClick={() => setPeriodPreset(preset.key)}
          >
            {preset.label}
          </button>
        ))}
      </div>
      {periodPreset === 'custom' && (
        <div className="date-range-inputs">
          <input
            type="date"
            value={customRange.start}
            onChange={(event) => setCustomRange({ ...customRange, start: event.target.value })}
          />
          <input
            type="date"
            value={customRange.end}
            onChange={(event) => setCustomRange({ ...customRange, end: event.target.value })}
          />
        </div>
      )}
    </div>
  )
}

function MetricCard({ label, value, detail }: { label: string; value: number; detail: string }) {
  return (
    <article className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  )
}

function DailyBars({ dailyCounts }: { dailyCounts: Array<{ date: string; count: number }> }) {
  const max = Math.max(1, ...dailyCounts.map((item) => item.count))
  const trimmed = dailyCounts.length > 42 ? dailyCounts.slice(-42) : dailyCounts
  return (
    <div className="daily-bars">
      {trimmed.map((item) => (
        <div className="daily-bar" key={item.date} title={`${item.date}: ${item.count}`}>
          <span style={{ height: `${Math.max(8, (item.count / max) * 100)}%` }} />
          <small>{formatShortDate(item.date)}</small>
        </div>
      ))}
    </div>
  )
}

function SelectedGarmentPanel({
  garment,
  records,
  wearCount,
}: {
  garment?: Garment
  records: WearRecord[]
  wearCount: number
}) {
  if (!garment) {
    return <EmptyState icon={Shirt} title="单件衣物数据" text="选择一件衣物查看明细。" />
  }

  return (
    <section className="detail-panel">
      <div className="block-heading">
        <h3>单件衣物数据</h3>
        <span>{categoryLabels[garment.category]}</span>
      </div>
      <div className="detail-hero">
        <GarmentImage garment={garment} />
        <div>
          <strong>{garment.name}</strong>
          <small>{garment.brand || '未填写品牌'}</small>
        </div>
      </div>
      <div className="detail-counts">
        <div>
          <span>总次数</span>
          <b>{wearCount}</b>
        </div>
        <div>
          <span>最近</span>
          <b>{records[0] ? formatShortDate(records[0].wornAt) : '-'}</b>
        </div>
      </div>
      <div className="detail-history">
        {records.slice(0, 4).map((record) => (
          <span key={record.id}>
            <History aria-hidden="true" size={14} />
            {formatReadableDate(record.wornAt)}
          </span>
        ))}
      </div>
    </section>
  )
}

function RecentRecords({
  records,
  garmentsById,
  onDelete,
}: {
  records: WearRecord[]
  garmentsById: Map<string, Garment>
  onDelete: (record: WearRecord) => void
}) {
  return (
    <section className="recent-panel">
      <div className="block-heading">
        <h3>最近记录</h3>
        <span>{records.length}</span>
      </div>
      <div className="recent-list">
        {records.map((record) => (
          <article className="recent-record" key={record.id}>
            <img src={record.evidencePhotoDataUrl} alt={`${record.wornAt} 穿着照片`} />
            <div>
              <strong>{formatReadableDate(record.wornAt)}</strong>
              <small>
                {categoryOrder
                  .map((category) => garmentsById.get(record.garmentIds[category] ?? '')?.name)
                  .filter(Boolean)
                  .join(' / ') || '未命名衣物'}
              </small>
            </div>
            <button type="button" className="icon-button" aria-label="删除记录" onClick={() => onDelete(record)}>
              <Trash2 aria-hidden="true" size={16} />
            </button>
          </article>
        ))}
        {!records.length && <EmptyState icon={CalendarDays} title="暂无记录" text="拍照保存后会出现在这里。" />}
      </div>
    </section>
  )
}

function GarmentImage({ garment }: { garment: Garment }) {
  return garment.imageDataUrl ? (
    <img className="garment-image" src={garment.imageDataUrl} alt={garment.name} />
  ) : (
    <div className="garment-image garment-fallback" style={{ background: garment.color }}>
      <CategoryIcon category={garment.category} />
    </div>
  )
}

function CategoryIcon({ category }: { category: GarmentCategory }) {
  if (category === 'top') {
    return <Shirt aria-hidden="true" size={20} />
  }
  if (category === 'shoes') {
    return <Footprints aria-hidden="true" size={20} />
  }
  return <Layers aria-hidden="true" size={20} />
}

function EmptyState({
  icon: Icon,
  title,
  text,
}: {
  icon: typeof Camera
  title: string
  text: string
}) {
  return (
    <div className="empty-state">
      <Icon aria-hidden="true" size={22} />
      <strong>{title}</strong>
      <span>{text}</span>
    </div>
  )
}

function describeCombo(selection: GarmentMap, garmentsById: Map<string, Garment>): string {
  const names = categoryOrder
    .map((category) => selection[category])
    .map((id) => (id ? garmentsById.get(id)?.name : undefined))
    .filter(Boolean)
  return names.length ? names.join(' / ') : '选择衣物生成组合预览'
}

function createId(prefix: string): string {
  const random = globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)
  return `${prefix}_${Date.now().toString(36)}_${random.slice(0, 8)}`
}

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

export default App
