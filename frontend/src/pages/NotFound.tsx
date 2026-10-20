import { useNavigate } from 'react-router-dom'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { ArrowRight, House } from '@/components/icons'
import { CARD_BASE } from '@/components/layout/PageShell'
import { GlassButton } from '@/components/ui/glass-button'

/*
  404 页。

  此前这里复用的是"施工中"占位组件 —— 于是一个拼错的 URL 会告诉用户
  "这个页面还在从 Vue 迁到 React 的路上"。那句话在半年前是真的，现在只会误导：
  用户会以为站点还没做完，而不是"这个地址不存在"。

  设计上刻意做得很轻：一个很大的 404 数字（等宽字体，与 eyebrow 同一套字），
  一句人话，两个出口。不加搜索框、不加推荐位 —— 掉进 404 的人只想尽快离开这一页。
*/

export default function NotFound() {
  const navigate = useNavigate()

  return (
    <div className="mx-auto flex min-h-[calc(100vh-56px)] w-full max-w-[720px] flex-col items-center justify-center px-6 py-16">
      <AnimatedContent trigger="mount" distance={28} duration={0.55} ease="power2.out" threshold={0}>
        <div className={`${CARD_BASE} w-full px-8 py-14 text-center`}>
          <p className="font-mono text-6xl font-semibold tracking-tight text-foreground/15 sm:text-7xl">
            404
          </p>

          <h1 className="mt-4 text-xl font-semibold tracking-tight sm:text-2xl">这个地址不存在</h1>
          <p className="mx-auto mt-2.5 max-w-sm text-sm leading-6 text-muted-foreground">
            链接可能拼错了，或者这条内容已经被删除。需求和项目都还在，从下面任意一个入口回去都可以。
          </p>

          <div className="mt-8 flex flex-col items-center justify-center gap-2.5 sm:flex-row">
            <GlassButton
              type="button"
              onClick={() => navigate('/tickets')}
              className="h-10 w-full px-6 text-sm sm:w-auto"
            >
              <ArrowRight size={15} />
              去需求大厅
            </GlassButton>

            <button
              type="button"
              onClick={() => navigate('/')}
              className="inline-flex h-10 w-full cursor-pointer items-center justify-center gap-1.5 rounded-full px-4 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground sm:w-auto"
            >
              <House size={15} />
              回到首页
            </button>
          </div>
        </div>
      </AnimatedContent>
    </div>
  )
}
