/**
 * 全站图标的**唯一出口**。
 *
 * 为什么需要这个文件：旧的 Vue 应用在 `main.ts` 里把 Element Plus 的**全部**图标
 * 注册成了全局组件，于是模板里写 `<Ticket />` 就行，不用 import。React 没有这个
 * 机制，每个图标都得显式 import —— 如果让 20 个页面各自从 'lucide-react' 里引，
 * 换图标库就要动 20 个文件，而且同一个语义在一处叫 Close、另一处叫 X。
 *
 * 所以：**任何页面都不许直接 import 'lucide-react'**，一律从这里取。
 *
 * 下面的注释标的是迁移前对应的 Element Plus 图标名 —— 对照旧模板时用得上。
 * 名字用的是 lucide 的原名，没有起别名：起别名（比如 `X as Close`）会让读代码的人
 * 以为 lucide 真有个叫 Close 的图标，反而更难查。
 */
export {
  Monitor,        // EP: Monitor
  Menu,           // EP: Menu
  X,              // EP: Close
  ArrowDown,      // EP: ArrowDown
  ArrowLeft,      // EP: ArrowLeft
  ArrowRight,     // EP: ArrowRight
  User,           // EP: User
  UserRound,      // EP: UserFilled（顶栏头像里的实心人像）
  Lock,           // EP: Lock
  Mail,           // EP: Message
  Check,          // EP: Check
  Send,           // EP: Promotion（纸飞机）
  Briefcase,      // EP: Suitcase
  Ticket,         // EP: Ticket
  Gift,           // EP: Present
  Medal,          // EP: Medal
  Clock,          // EP: Clock
  Calendar,       // EP: Calendar
  Star,           // EP: Star
  Coins,          // EP: Coin
  Download,       // EP: Download
  Upload,         // EP: Upload（交付压缩包的上传入口）
  FileText,       // EP: Document
  FileArchive,    // EP: Files（交付物 = 压缩包）
  Folder,         // EP: Folder
  WandSparkles,   // EP: MagicStick
  MessageCircle,  // EP: ChatDotRound
  Info,           // EP: InfoFilled
  Ellipsis,       // EP: More（lucide 的 MoreHorizontal 现名）
  Plus,           // EP: Plus
  LoaderCircle,   // EP: Loading（需要自己加 animate-spin）
  CircleCheck,    // EP: CircleCheck / CircleCheckFilled
  CircleCheckBig, // EP: Finished
  CircleX,        // EP: CircleCloseFilled
  TriangleAlert,  // EP: Warning
  CircleAlert,    // EP: WarningFilled
  Flag,           // EP: Finished（统计页的"已完成"用的是旗子）
  House,          // EP: HomeFilled（404 页的"回到首页"）
  ShieldCheck,    // EP: Lock/UserFilled 之间（管理员区页头用）
  Wallet,         // EP: Wallet（钱包页与"我的钱包"入口）
  Receipt,        // EP: Tickets（充值记录/流水）
  Users,          // EP: User（用户管理页头）
  ChartColumn,    // EP: DataLine（统计页头）
  Trash2          // EP: Delete（用户管理页的删除）
} from 'lucide-react'
